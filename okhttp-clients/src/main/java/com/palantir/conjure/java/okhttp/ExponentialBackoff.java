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

package com.palantir.conjure.java.okhttp;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Implements "exponential backoff with full jitter", suggesting a backoff duration chosen randomly from the interval
 * {@code [0, backoffSlotSize * 2^c)} for the c-th retry for a maximum of {@link #maxNumRetries} retries.
 */
final class ExponentialBackoff implements BackoffStrategy {

    private final int maxNumRetries;
    private final Duration backoffSlotSize;
    private final Supplier<Random> random;

    private int retryNumber = 0;

    ExponentialBackoff(int maxNumRetries, Duration backoffSlotSize) {
        this(maxNumRetries, backoffSlotSize, ThreadLocalRandom::current);
    }

    @VisibleForTesting
    ExponentialBackoff(int maxNumRetries, Duration backoffSlotSize, Supplier<Random> random) {
        this.maxNumRetries = maxNumRetries;
        this.backoffSlotSize = backoffSlotSize;
        this.random = random;
    }

    @Override
    public Optional<Duration> nextBackoff() {
        retryNumber += 1;
        if (retryNumber > maxNumRetries) {
            return Optional.empty();
        }

        int upperBound = (int) Math.pow(2, retryNumber);
        return Optional.of(
                Duration.ofNanos(Math.round(backoffSlotSize.toNanos() * random.get().nextDouble() * upperBound)));
    }
}
