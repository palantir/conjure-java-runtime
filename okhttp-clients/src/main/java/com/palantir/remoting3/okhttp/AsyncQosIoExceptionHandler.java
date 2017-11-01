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

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
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
    private final BackoffStrategy backoffStrategy;

    AsyncQosIoExceptionHandler(
            ScheduledExecutorService scheduledExecutorService,
            BackoffStrategy backoffStrategy) {
        this.scheduledExecutorService = MoreExecutors.listeningDecorator(scheduledExecutorService);
        this.backoffStrategy = backoffStrategy;
    }

    @Override
    public Response handle(QosIoExceptionAwareCall call, QosIoException qosIoException) throws IOException {
        Duration backoff = getNextBackoffOrRethrow(qosIoException);

        return retry(call, backoff);
    }

    @Override
    public void handleAsync(QosIoExceptionAwareCall call, QosIoException qosIoException, Callback callback) {
        try {
            Duration backoff = getNextBackoffOrRethrow(qosIoException);

            retryAsync(call, backoff, callback);
        } catch (IOException e) {
            callback.onFailure(call, e);
        } catch (Throwable t) {
            callback.onFailure(call, new IOException("Unexpected error during async retry", t));
        }
    }

    private Duration getNextBackoffOrRethrow(QosIoException qosIoException) throws IOException {
        return getNextBackoff(qosIoException)
                .orElseThrow(() -> qosIoException);
    }

    private Optional<Duration> getNextBackoff(QosIoException qosIoException) {
        return qosIoException.getQosException().accept(new QosException.Visitor<Optional<Duration>>() {
            @Override
            public Optional<Duration> visit(QosException.Throttle exception) {
                return exception.getRetryAfter().isPresent()
                        ? exception.getRetryAfter()
                        : backoffStrategy.nextBackoff();
            }

            @Override
            public Optional<Duration> visit(QosException.RetryOther exception) {
                // TODO(nziebart): is it ok to throw a runtime exception here? If not, Visitor needs to declare that
                // it throws IOException, and this should be an IOException
                throw new IllegalStateException(
                        "Internal error, did not expect to handle RetryOther exception in handler", exception);
            }

            @Override
            public Optional<Duration> visit(QosException.Unavailable exception) {
                return backoffStrategy.nextBackoff();
            }
        });
    }

    private void retryAsync(QosIoExceptionAwareCall call, Duration backoff, Callback callback) {
        scheduledExecutorService.schedule(
                () -> call.clone().enqueue(callback),
                backoff.toMillis(), TimeUnit.MILLISECONDS);
    }

    private Response retry(QosIoExceptionAwareCall call, Duration backoff) throws IOException {
        log.debug("Rescheduling call after backoff", SafeArg.of("backoffMillis", backoff.toMillis()));
        sleepForBackoff(backoff);

        return call.clone().execute();
    }

    private void sleepForBackoff(Duration duration) throws IOException {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            throw new InterruptedIOException("Interrupted while pausing for retry backoff");
        }
    }
}
