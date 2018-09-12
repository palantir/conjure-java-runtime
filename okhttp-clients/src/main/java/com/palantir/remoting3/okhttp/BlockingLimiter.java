/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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
 * @param <ContextT>
 */
public final class BlockingLimiter<ContextT> implements Limiter<ContextT> {
    public static <ContextT> BlockingLimiter<ContextT> wrap(Limiter<ContextT> delegate) {
        return new BlockingLimiter<>(Clock.systemUTC(), delegate, Optional.empty());
    }

    public static <ContextT> BlockingLimiter<ContextT> wrap(Limiter<ContextT> delegate, Duration timeout) {
        return new BlockingLimiter<>(Clock.systemUTC(), delegate, Optional.of(timeout));
    }

    private final Limiter<ContextT> delegate;
    private final Clock clock;
    private final Optional<Duration> timeout;

    /**
     * Lock used to block and unblock callers as the limit is reached
     */
    private final Object lock = new Object();

    private BlockingLimiter(Clock clock, Limiter<ContextT> limiter, Optional<Duration> timeout) {
        this.clock = clock;
        this.delegate = limiter;
        this.timeout = timeout;
    }
    
    private Optional<Listener> tryAcquire(ContextT context) {
        Optional<Instant> timeoutTime = timeout.map(timeout -> clock.instant().plus(timeout));
        synchronized (lock) {
            while (true) {
                Optional<Duration> durationRemaining = timeoutTime.map(t -> Duration.between(clock.instant(), t));
                if (durationRemaining.isPresent()
                        && (durationRemaining.get().isNegative() || durationRemaining.get().isZero())) {
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
                    if (durationRemaining.isPresent() && !durationRemaining.get().isZero()) {
                        lock.wait(durationRemaining.get().toMillis());
                    } else {
                        lock.wait();
                    }
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
        return tryAcquire(context).map(delegate -> new Listener() {
            @Override
            public void onSuccess() {
                delegate.onSuccess();
                unblock();
            }

            @Override
            public void onIgnore() {
                delegate.onIgnore();
                unblock();
            }

            @Override
            public void onDropped() {
                delegate.onDropped();
                unblock();
            }
        });
    }
    
    @Override
    public String toString() {
        return "BlockingLimiter [" + delegate + "]";
    }
}
