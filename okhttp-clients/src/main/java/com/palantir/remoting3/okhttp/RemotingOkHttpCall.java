/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.remoting3.okhttp;

import com.google.common.util.concurrent.SettableFuture;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting.api.errors.QosException;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting3.clients.ClientConfiguration;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO(rfink): Consider differentiating the IOExceptions thrown/returned by this class, add better error messages, #628

/**
 * An OkHttp {@link Call} implementation that handles standard retryable error status such as 308, 429, 503, and
 * connection errors. Calls are rescheduled on a given scheduler and executed on a given executor.
 */
final class RemotingOkHttpCall extends ForwardingCall {

    private static final Logger log = LoggerFactory.getLogger(RemotingOkHttpCall.class);

    private static final ResponseHandler<RemoteException> remoteExceptionHandler =
            RemoteExceptionResponseHandler.INSTANCE;
    private static final ResponseHandler<IOException> ioExceptionHandler = IoExceptionResponseHandler.INSTANCE;
    private static final ResponseHandler<QosException> qosHandler = QosExceptionResponseHandler.INSTANCE;

    private final BackoffStrategy backoffStrategy;
    private final UrlSelector urls;
    private final RemotingOkHttpClient client;
    private final ScheduledExecutorService schedulingExecutor;
    private final ExecutorService executionExecutor;

    private final int maxNumRelocations;

    RemotingOkHttpCall(
            Call delegate,
            BackoffStrategy backoffStrategy,
            UrlSelector urls,
            RemotingOkHttpClient client,
            ScheduledExecutorService schedulingExecutor,
            ExecutorService executionExecutor,
            int maxNumRelocations) {
        super(delegate);
        this.backoffStrategy = backoffStrategy;
        this.urls = urls;
        this.client = client;
        this.schedulingExecutor = schedulingExecutor;
        this.executionExecutor = executionExecutor;
        this.maxNumRelocations = maxNumRelocations;
    }

    /**
     * Process the call. If an IOException is encountered, mark the URL as failed, which indicates that it should
     * be avoided for subsequent calls (if {@link UrlSelector} was initialized with a positive
     * {@link ClientConfiguration#failedUrlCooldown()}.
     */
    @Override
    public Response execute() throws IOException {
        SettableFuture<Response> future = SettableFuture.create();
        enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                future.setException(exception);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                future.set(response);
            }
        });

        try {
            // We don't enforce a timeout here because it's not possible to know how long this operation might take.
            // First, it might get queued indefinitely in the Dispatcher, and then it might get retried a (potentially)
            // unknown amount of times by the BackoffStrategy. The {@code get} call times out when the underlying
            // OkHttp call times out (, possibly after a number of retries).
            return future.get();
        } catch (InterruptedException e) {
            getDelegate().cancel();
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Call was interrupted during execution");
        } catch (ExecutionException e) {
            getDelegate().cancel();
            if (e.getCause() instanceof IoRemoteException) {
                // TODO(rfink): Consider unwrapping the RemoteException at the Retrofit/Feign layer for symmetry, #626
                RemoteException wrappedException = ((IoRemoteException) e.getCause()).getWrappedException();
                RemoteException correctStackTrace = new RemoteException(
                        wrappedException.getError(),
                        wrappedException.getStatus());
                correctStackTrace.initCause(e);
                throw correctStackTrace;
            } else if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw new IOException("Failed to execute call", e);
            }
        }
    }

    private static Response buildFrom(Response unbufferedResponse, byte[] bodyBytes) {
        return unbufferedResponse.newBuilder()
                .body(buffer(unbufferedResponse.body().contentType(), bodyBytes))
                .build();
    }

    private static ResponseBody buffer(MediaType mediaType, byte[] bodyBytes) {
        return ResponseBody.create(mediaType, bodyBytes);
    }

    @Override
    public void enqueue(Callback callback) {
        super.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                urls.markAsFailed(request().url());

                // Fail call if backoffs are exhausted or if no retry URL can be determined.
                Optional<Duration> backoff = backoffStrategy.nextBackoff();
                if (!backoff.isPresent()) {
                    callback.onFailure(call, new IOException("Failed to complete the request due to an "
                            + "IOException", exception));
                    return;
                }
                Optional<HttpUrl> redirectTo = urls.redirectToNext(request().url());
                if (!redirectTo.isPresent()) {
                    callback.onFailure(call, new IOException("Failed to determine valid failover URL"
                            + "for '" + request().url() + "' and base URLs " + urls.getBaseUrls()));
                    return;
                }

                Request redirectedRequest = request().newBuilder()
                        .url(redirectTo.get())
                        .build();
                RemotingOkHttpCall retryCall =
                        client.newCallWithMutableState(redirectedRequest, backoffStrategy, maxNumRelocations - 1);
                log.debug("Rescheduling call after backoff", SafeArg.of("backoffMillis", backoff.get().toMillis()),
                        exception);
                scheduleExecution(() -> retryCall.enqueue(callback), backoff.get());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // Relay successful responses
                if (response.code() / 100 <= 2) {
                    callback.onResponse(call, response);
                    return;
                }

                // Buffer the response into a byte[] so that multiple handler can safely consume the body.
                // This consumes and closes the original response body.
                Supplier<Response> errorResponseSupplier;
                try {
                    byte[] body = response.body().bytes();
                    // so error handlers can read the body without breaking subsequent handlers
                    errorResponseSupplier = () -> buildFrom(response, body);
                } catch (IOException e) {
                    onFailure(call, e);
                    return;
                }

                // Handle to handle QoS situations: retry, failover, etc.
                Optional<QosException> qosError = qosHandler.handle(errorResponseSupplier.get());
                if (qosError.isPresent()) {
                    qosError.get().accept(createQosVisitor(callback, call));
                    return;
                }

                // Handle responses that correspond to RemoteExceptions / SerializableErrors
                Optional<RemoteException> httpError = remoteExceptionHandler.handle(errorResponseSupplier.get());
                if (httpError.isPresent()) {
                    callback.onFailure(call, new IoRemoteException(httpError.get()));
                    return;
                }

                // Catch-all: handle all other responses
                Optional<IOException> ioException = ioExceptionHandler.handle(errorResponseSupplier.get());
                if (ioException.isPresent()) {
                    callback.onFailure(call, ioException.get());
                    return;
                }

                callback.onFailure(call, new IOException("Failed to handle request, this is an http-remoting bug."));
            }
        });
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void scheduleExecution(Runnable execution, Duration backoff) {
        // TODO(rfink): Investigate whether ignoring the ScheduledFuture is safe, #629.
        schedulingExecutor.schedule(
                () -> executionExecutor.submit(execution),
                backoff.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private QosException.Visitor<Void> createQosVisitor(Callback callback, Call call) {
        return new QosException.Visitor<Void>() {
            @Override
            public Void visit(QosException.Throttle exception) {
                Optional<Duration> nonAdvertizedBackoff = backoffStrategy.nextBackoff();
                if (!nonAdvertizedBackoff.isPresent()) {
                    callback.onFailure(call, new IOException("Failed to reschedule call since "
                            + "the number of configured backoffs are exhausted", exception));
                    return null;
                }

                Duration backoff = exception.getRetryAfter().orElse(nonAdvertizedBackoff.get());
                log.debug("Rescheduling call after backoff", SafeArg.of("backoffMillis", backoff.toMillis()),
                        exception);
                scheduleExecution(() -> doClone().enqueue(callback), backoff);
                return null;
            }

            @Override
            public Void visit(QosException.RetryOther exception) {
                if (maxNumRelocations <= 0) {
                    callback.onFailure(call, new IOException("Exceeded the maximum number of allowed redirects for "
                            + "initial URL: " + call.request().url()));
                } else {
                    // Redirect to the URL specified by the exception.
                    Optional<HttpUrl> redirectTo = urls.redirectTo(request().url(),
                            exception.getRedirectTo().toString());
                    if (!redirectTo.isPresent()) {
                        callback.onFailure(call, new IOException("Failed to determine valid redirect URL for '"
                                + exception.getRedirectTo() + "' and base URLs " + urls.getBaseUrls()));
                    } else {
                        Request redirectedRequest = request().newBuilder()
                                .url(redirectTo.get())
                                .build();
                        client.newCallWithMutableState(redirectedRequest, backoffStrategy, maxNumRelocations - 1)
                                .enqueue(callback);
                    }
                }
                return null;
            }

            @Override
            public Void visit(QosException.Unavailable exception) {
                Optional<Duration> backoff = backoffStrategy.nextBackoff();
                if (!backoff.isPresent()) {
                    log.debug("Max number of retries exceeded, failing call");
                    callback.onFailure(call,
                            new IOException("Failed to complete the request due to a "
                                    + "server-side QoS condition: 503", exception));
                } else {
                    log.debug("Rescheduling call after backoff",
                            SafeArg.of("backoffMillis", backoff.get().toMillis()), exception);
                    // Redirect to the "next" URL, whichever that may be, after backing off.
                    Optional<HttpUrl> redirectTo = urls.redirectToNext(request().url());
                    if (!redirectTo.isPresent()) {
                        callback.onFailure(call, new IOException("Failed to determine valid redirect URL for base "
                                + "URLs " + urls.getBaseUrls()));
                    } else {
                        Request redirectedRequest = request().newBuilder()
                                .url(redirectTo.get())
                                .build();
                        scheduleExecution(() -> client.newCallWithMutableState(redirectedRequest, backoffStrategy,
                                maxNumRelocations).enqueue(callback), backoff.get());
                    }
                }
                return null;
            }
        };
    }

    // TODO(rfink): Consider removing RemotingOkHttpCall#doClone method, #627
    @Override
    public RemotingOkHttpCall doClone() {
        return new RemotingOkHttpCall(getDelegate().clone(), backoffStrategy, urls, client, schedulingExecutor,
                executionExecutor, maxNumRelocations);
    }
}
