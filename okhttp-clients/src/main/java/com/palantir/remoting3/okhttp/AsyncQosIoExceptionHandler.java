/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspects the {@link QosException} thrown by a {@link Call} execution and -- depending on the type of {@link
 * QosException} -- schedules a future execution of the call on the configured {@link ExecutorService}.
 */
class AsyncQosIoExceptionHandler implements QosIoExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncQosIoExceptionHandler.class);

    private final ListeningScheduledExecutorService scheduledExecutorService;
    private final ListeningExecutorService executorService;
    private final BackoffStrategy backoffStrategy;

    private final MultiServerRequestCreator requestCreator;
    private final Call.Factory callFactory;

    AsyncQosIoExceptionHandler(
            ScheduledExecutorService scheduledExecutorService,
            ExecutorService executorService,
            BackoffStrategy backoffStrategy,
            MultiServerRequestCreator requestCreator,
            Call.Factory callFactory) {
        Preconditions.checkArgument(scheduledExecutorService != executorService,
                "Almost certainly you want these to be different - need fixed pool vs cached.");
        this.scheduledExecutorService = MoreExecutors.listeningDecorator(scheduledExecutorService);
        this.executorService = MoreExecutors.listeningDecorator(executorService);
        this.backoffStrategy = backoffStrategy;
        this.requestCreator = requestCreator;
        this.callFactory = request -> new QosIoExceptionAwareCall(callFactory.newCall(request), this);
    }

    @Override
    public ListenableFuture<Response> handle(QosIoExceptionAwareCall call, QosIoException qosIoException) {
        return qosIoException.getQosException().accept(new QosException.Visitor<ListenableFuture<Response>>() {
            @Override
            public ListenableFuture<Response> visit(QosException.Throttle exception) {
                Optional<Duration> backoff = exception.getRetryAfter().isPresent()
                        ? exception.getRetryAfter()
                        : backoffStrategy.nextBackoff();

                if (!backoff.isPresent()) {
                    log.debug("No backoff advertised, failing call");
                    return Futures.immediateFailedFuture(qosIoException);
                } else {
                    log.debug("Rescheduling call after backoff", SafeArg.of("backoffMillis", backoff.get().toMillis()));
                    return retry(call.clone(), backoff.get());
                }
            }

            @Override
            public ListenableFuture<Response> visit(QosException.RetryOther exception) {
                // Redirects are handled in QosRetryLaterInterceptor.
                return Futures.immediateFailedFuture(new IOException(
                        "Internal error, did not expect to handle RetryOther exception in handler", exception));
            }

            @Override
            public ListenableFuture<Response> visit(QosException.Unavailable exception) {
                Optional<Duration> backoff = backoffStrategy.nextBackoff();
                if (!backoff.isPresent()) {
                    log.debug("No backoff advertised, failing call");
                    return Futures.immediateFailedFuture(qosIoException);
                }

                Request newRequest;
                try {
                    newRequest = requestCreator.getNextRequest(call.request());
                } catch (IOException e) {
                    log.debug("Could not find a suitable subsequent server, failing call");
                    return Futures.immediateFailedFuture(qosIoException);
                }

                log.debug("Rescheduling call on a new host, after backoff",
                        SafeArg.of("backoffMillis", backoff.get().toMillis()),
                        SafeArg.of("host", newRequest.url().host())); // Must be host, url itself is unsafe
                return retry(callFactory.newCall(newRequest), backoff.get());
            }
        });
    }

    // Have to schedule the retry on a different thread to avoid deadlocking a fixed size thread pool.
    private ListenableFuture<Response> retry(Call call, Duration backoff) {
        Preconditions.checkState(call instanceof QosIoExceptionAwareCall,
                "Attempted to retry with a call that is not QoSIoExceptionAware");
        ListenableFuture<ListenableFuture<Response>> result =
                scheduledExecutorService.schedule(
                        () -> executorService.submit(call::execute),
                        backoff.toMillis(), TimeUnit.MILLISECONDS);
        return Futures.dereference(result);
    }
}
