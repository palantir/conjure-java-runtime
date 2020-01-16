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

import com.netflix.concurrency.limits.Limit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Changes made from {@link com.netflix.concurrency.limits.limit.WindowedLimit}:
 *
 * <p>1. Modify to reduce window size whenever a dropped sample is seen, rather than awaiting the whole window. 2.
 * Change package and make package private. 3. Code style. 4. Inlined constants.
 */
class ConjureWindowedLimit implements Limit {
    private static final long MIN_WINDOW_TIME = TimeUnit.SECONDS.toNanos(1);
    private static final long MAX_WINDOW_TIME = TimeUnit.SECONDS.toNanos(1);
    private static final long MIN_RTT_THRESHOLD = TimeUnit.MICROSECONDS.toNanos(100);

    /** Minimum observed samples to filter out sample windows with not enough significant samples. */
    private static final int WINDOW_SIZE = 10;

    private final Limit delegate;

    /** End time for the sampling window at which point the limit should be updated. */
    private volatile long nextUpdateTime = 0;

    private final Object lock = new Object();

    /** Object tracking stats for the current sample window. */
    private final AtomicReference<ImmutableSampleWindow> sample = new AtomicReference<>(new ImmutableSampleWindow());

    ConjureWindowedLimit(Limit delegate) {
        this.delegate = delegate;
    }

    @Override
    public void notifyOnChange(Consumer<Integer> consumer) {
        delegate.notifyOnChange(consumer);
    }

    @Override
    public void onSample(long startTime, long rtt, int inflight, boolean didDrop) {
        long endTime = startTime + rtt;

        if (rtt < MIN_RTT_THRESHOLD) {
            return;
        }

        final ImmutableSampleWindow currentSample;
        if (didDrop) {
            currentSample = sample.updateAndGet(current -> current.addDroppedSample(inflight));
        } else {
            currentSample = sample.updateAndGet(window -> window.addSample(rtt, inflight));
        }

        if (endTime > nextUpdateTime || currentSample.didDrop()) {
            synchronized (lock) {
                // Double check under the lock
                if (endTime > nextUpdateTime || sample.get().didDrop()) {
                    ImmutableSampleWindow current = sample.get();
                    if (isWindowReady(current)) {
                        sample.set(new ImmutableSampleWindow());
                        nextUpdateTime = endTime
                                + Math.min(
                                        Math.max(current.getCandidateRttNanos() * 2, MIN_WINDOW_TIME), MAX_WINDOW_TIME);
                        // +1 ensures that average rtt in nanos is never 0, which has a precond check in VegasLimit.
                        delegate.onSample(
                                startTime, current.getAverageRttNanos() + 1, current.getMaxInFlight(), didDrop);
                    }
                }
            }
        }
    }

    private static boolean isWindowReady(ImmutableSampleWindow sample) {
        return sample.didDrop() || sample.getSampleCount() > WINDOW_SIZE;
    }

    @Override
    public int getLimit() {
        return delegate.getLimit();
    }
}
