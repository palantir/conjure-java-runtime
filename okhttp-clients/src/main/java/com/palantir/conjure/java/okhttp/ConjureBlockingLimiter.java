/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Copyright 2018 Netflix, Inc.
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
package com.palantir.conjure.java.okhttp;

import com.netflix.concurrency.limits.Limiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * {@link Limiter} that blocks the caller when the limit has been reached.  The caller is
 * blocked until the limiter has been released.  This limiter is commonly used in batch
 * clients that use the limiter as a back-pressure mechanism.
 *
 * This limiter has been forked from Netflix's {@link com.netflix.concurrency.limits.limiter.BlockingLimiter}.
 *
 * Changes are:
 * 1. Modified to support a 'timeout', rather than blocking forever.
 * 2. Codestyle.
 * 3. Made package private.
 * 4. Palantir license.
 * 5. Renamed to ConjureBlockingLimiter and changed package in order to avoid ambiguity.
 * <p>
 * TODO(j-baker): Remove once https://github.com/Netflix/concurrency-limits/pull/78 is merged and released.
 */
final class ConjureBlockingLimiter<ContextT> implements Limiter<ContextT> {
    static <ContextT> ConjureBlockingLimiter<ContextT> wrap(Limiter<ContextT> delegate, Duration timeout) {
        return new ConjureBlockingLimiter<>(Clock.systemUTC(), delegate, timeout);
    }

    private final Limiter<ContextT> delegate;
    private final Clock clock;
    private final Duration timeout;

    /**
     * Lock used to block and unblock callers as the limit is reached.
     */
    private final Object lock = new Object();

    private ConjureBlockingLimiter(Clock clock, Limiter<ContextT> limiter, Duration timeout) {
        this.clock = clock;
        this.delegate = limiter;
        this.timeout = timeout;
    }

    private Optional<Listener> tryAcquire(ContextT context) {
        Instant timeoutTime = clock.instant().plus(timeout);
        synchronized (lock) {
            while (true) {
                Duration remaining = Duration.between(clock.instant(), timeoutTime);
                if (remaining.compareTo(Duration.ZERO) <= 0) {
                    return Optional.empty();
                }

                // Try to acquire a token and return immediately if successful
                Optional<Listener> listener;
                listener = delegate.acquire(context);
                if (listener.isPresent()) {
                    return listener;
                }

                // We have reached the limit so block until a token is released
                try {
                    lock.wait(remaining.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        }
    }

    private void unblock() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    @Override
    public Optional<Listener> acquire(ContextT context) {
        return tryAcquire(context).map(delegateListener -> new Listener() {
            @Override
            public void onSuccess() {
                delegateListener.onSuccess();
                unblock();
            }

            @Override
            public void onIgnore() {
                delegateListener.onIgnore();
                unblock();
            }

            @Override
            public void onDropped() {
                delegateListener.onDropped();
                unblock();
            }
        });
    }

    @Override
    public String toString() {
        return "ConjureBlockingLimiter [" + delegate + "]";
    }
}
