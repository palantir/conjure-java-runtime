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
class QosAwareCallRetrier implements CallRetrier {

    private static final Logger log = LoggerFactory.getLogger(QosAwareCallRetrier.class);

    private final BackoffStrategy backoffStrategy;
    private final BackoffSleeper backoffSleeper;
    private final ScheduledExecutorService scheduler;

    QosAwareCallRetrier(
            BackoffStrategy backoffStrategy,
            BackoffSleeper backoffSleeper,
            ScheduledExecutorService asyncRetryScheduler) {
        this.backoffStrategy = backoffStrategy;
        this.backoffSleeper = backoffSleeper;
        this.scheduler = asyncRetryScheduler;
    }

    @Override
    public Response executeWithRetry(Call originalCall) throws IOException {
        for (Call call = originalCall; ; call = call.clone()) {
            try {
                return call.execute();
            } catch (QosIoException e) {
                backoffOrRethrow(e);
            }
        }
    }

    private void backoffOrRethrow(QosIoException e) throws IOException {
        Duration backoff = backoffStrategy.nextBackoff(e).orElseThrow(() -> e);
        backoffSleeper.sleepForBackoff(backoff);
    }

    @Override
    public void enqueueWithRetry(Call call, Callback callback) {
        enqueueAsyncWithRetry(call, callback, Duration.ofMillis(0L));
    }

    private void enqueueAsyncWithRetry(Call call, Callback callback, Duration backoff) {
        QosAwareCallback qosAwareCallback = new QosAwareCallback(callback, call);

        scheduler.schedule(() -> call.enqueue(qosAwareCallback), backoff.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void backoffAsyncOrFail(Call call, QosIoException qosIoException, Callback callback) {
        Optional<Duration> backoff = backoffStrategy.nextBackoff(qosIoException);

        if (backoff.isPresent()) {
            enqueueAsyncWithRetry(call, callback, backoff.get());
        } else {
            callback.onFailure(call, qosIoException);
        }
    }

    class QosAwareCallback implements Callback {
        private final Callback delegate;
        private final Call call;

        public QosAwareCallback(Callback delegate, Call call) {
            this.delegate = delegate;
            this.call = call;
        }

        @Override
        public void onFailure(Call call, IOException ioException) {
            if (!(ioException instanceof QosIoException)) {
                delegate.onFailure(call, ioException);
                return;
            }

            try {
                backoffAsyncOrFail(call.clone(), (QosIoException) ioException, delegate);
            } catch (Throwable t) {
                // This is a background thread, so we must propagate all exceptions to the callback
                delegate.onFailure(call, new IOException("Failed to execute request", t));
            }
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            delegate.onResponse(call, response);
        }
    }

}
