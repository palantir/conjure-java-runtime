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

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.Strategy;
import com.netflix.concurrency.limits.Strategy.Token;
import com.netflix.concurrency.limits.limit.VegasLimit;
import com.netflix.concurrency.limits.limiter.ImmutableSampleWindow;
import com.netflix.concurrency.limits.strategy.SimpleStrategy;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.concurrent.GuardedBy;

/**
 * This class is heavily based on Netflix's {@link com.netflix.concurrency.limits.limiter.DefaultLimiter}, and omits
 * the license header because that class, while claimed in the repository to be Apache 2.0 licensed, itself contains
 * <a href="https://github.com/Netflix/concurrency-limits/issues/73">no license header</a>.
 * <p>
 * Changes are as follows:
 * <ol>
 * <li>Fix concurrency bug - upstream has a race between starting finishing the window and running requests.</li>
 * <li>Store the last update time as well as the next update time.</li>
 * <li>Use the last update time to pre-emptively close the window when a request is dropped, instead of waiting.</li>
 * <li>Remove the builder, and instead have a static factory method.</li>
 * <li>Change code style to match Palantir baseline.</li>
 * </ol>
 */
final class RemotingConcurrencyLimiter implements Limiter<Void> {
    private final Object lock = new Object();

    private final LongSupplier nanoClock = System::nanoTime;
    private final Strategy<Void> strategy;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicReference<ImmutableSampleWindow> sample = new AtomicReference<>(new ImmutableSampleWindow());
    private final long minRttThreshold;
    private final long minWindowTime;
    private final long maxWindowTime;
    private final int windowSize;
    private final Limit limit;

    private volatile long lastUpdateTime = nanoClock.getAsLong();
    private volatile long nextUpdateTime = 0;

    RemotingConcurrencyLimiter(Strategy<Void> strategy, long minRttThreshold, long minWindowTime,
            long maxWindowTime, int windowSize, Limit limit) {
        this.strategy = strategy;
        this.minRttThreshold = minRttThreshold;
        this.minWindowTime = minWindowTime;
        this.maxWindowTime = maxWindowTime;
        this.windowSize = windowSize;
        this.limit = limit;
    }

    static RemotingConcurrencyLimiter createDefault(int initialLimit) {
        return new RemotingConcurrencyLimiter(
                new SimpleStrategy<>(),
                TimeUnit.MICROSECONDS.toNanos(100),
                TimeUnit.SECONDS.toNanos(1),
                TimeUnit.SECONDS.toNanos(1),
                100,
                VegasLimit.newBuilder().initialLimit(initialLimit).build());
    }

    @Override
    public Optional<Listener> acquire(Void context) {
        Token token = strategy.tryAcquire(context);
        if (!token.isAcquired()) {
            return Optional.empty();
        }
        long startTime = nanoClock.getAsLong();
        int currentMaxInFlight = inFlight.incrementAndGet();

        return Optional.of(new Listener() {
            @Override
            public void onSuccess() {
                inFlight.decrementAndGet();
                token.release();

                long endTime = nanoClock.getAsLong();
                long rtt = endTime - startTime;

                if (rtt < minRttThreshold) {
                    return;
                }
                sample.updateAndGet(window -> window.addSample(rtt, currentMaxInFlight));
                maybeUpdateWindow(endTime, () -> endTime > nextUpdateTime, window -> isWindowReady(window));
            }

            @Override
            public void onIgnore() {
                inFlight.decrementAndGet();
                token.release();
            }

            @Override
            public void onDropped() {
                inFlight.decrementAndGet();
                token.release();
                sample.getAndUpdate(current -> current.addDroppedSample(currentMaxInFlight));
                maybeUpdateWindow(System.nanoTime(), () -> startTime > lastUpdateTime, unused -> true);
            }

        });
    }

    private void maybeUpdateWindow(long endTime,
            Supplier<Boolean> shouldTry, Predicate<ImmutableSampleWindow> shouldFlush) {
        if (shouldTry.get()) {
            synchronized (lock) {
                if (shouldTry.get()) {
                    updateWindow(endTime, shouldFlush);
                }
            }
        }
    }

    @GuardedBy("lock")
    private void updateWindow(long endTime, Predicate<ImmutableSampleWindow> shouldFlush) {
        ImmutableSampleWindow currentWindow = sample.get();
        ImmutableSampleWindow newWindow = sample.updateAndGet(sampleWindow -> {
            if (shouldFlush.test(sampleWindow)) {
                return new ImmutableSampleWindow();
            } else {
                return sampleWindow;
            }
        });
        if (newWindow != currentWindow) {
            nextUpdateTime = endTime + Math.min(
                    Math.max(currentWindow.getCandidateRttNanos() * 2, minWindowTime),
                    maxWindowTime);
            limit.update(currentWindow);
            strategy.setLimit(limit.getLimit());
        }
    }

    private boolean isWindowReady(ImmutableSampleWindow sample) {
        return sample.getCandidateRttNanos() < Long.MAX_VALUE && sample.getSampleCount() > windowSize;
    }
}
