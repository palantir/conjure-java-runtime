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

import com.palantir.logsafe.SafeArg;
import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspects the {@link QosException} thrown by a {@link Call} execution and -- depending on the type of {@link
 * QosException} -- pauses for backoff and retries the call.
 */
class QosIoExceptionHandlerImpl implements QosIoExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(QosIoExceptionHandlerImpl.class);

    private final BackoffStrategy backoffStrategy;
    private final BackoffSleeper backoffSleeper;
    private final ScheduledExecutorService scheduler;

    QosIoExceptionHandlerImpl(
            BackoffStrategy backoffStrategy,
            BackoffSleeper backoffSleeper,
            ScheduledExecutorService asyncRetryScheduler) {
        this.backoffStrategy = backoffStrategy;
        this.backoffSleeper = backoffSleeper;
        this.scheduler = asyncRetryScheduler;
    }

    @Override
    public Response handle(QosIoExceptionAwareCall call, QosIoException qosIoException) throws IOException {
        Duration backoff = getNextBackoff(qosIoException)
                .orElseThrow(() -> qosIoException);

        return retry(call, backoff);
    }

    @Override
    public void handleAsync(QosIoExceptionAwareCall call, QosIoException qosIoException, Callback callback) {
        Optional<Duration> backoff = getNextBackoff(qosIoException);
        if (!backoff.isPresent()) {
            callback.onFailure(call, qosIoException);
            return;
        }

        retryAsync(call, backoff.get(), callback);
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
                throw new IllegalStateException(
                        "Internal error, did not expect to handle RetryOther exception in handler", exception);
            }

            @Override
            public Optional<Duration> visit(QosException.Unavailable exception) {
                return backoffStrategy.nextBackoff();
            }
        });
    }

    private Response retry(QosIoExceptionAwareCall call, Duration backoff) throws IOException {
        log.debug("Rescheduling call after backoff", SafeArg.of("backoffMillis", backoff.toMillis()));
        backoffSleeper.sleepForBackoff(backoff);

        return call.clone().execute();
    }

    private void retryAsync(QosIoExceptionAwareCall call, Duration backoff, Callback callback) {
        log.debug("Rescheduling async call after backoff", SafeArg.of("backoffMillis", backoff.toMillis()));

        scheduler.schedule(() -> call.clone().enqueue(callback), backoff.toMillis(), TimeUnit.MILLISECONDS);
    }

}
