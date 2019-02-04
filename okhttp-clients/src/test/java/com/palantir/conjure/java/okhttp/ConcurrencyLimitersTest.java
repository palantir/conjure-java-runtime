/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.Test;

public final class ConcurrencyLimitersTest {
    private static final String KEY = "";
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private final ConcurrencyLimiters limiters = new ConcurrencyLimiters(
            Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                    .setNameFormat("listener-reviver")
                    .build()),
            new DefaultTaggedMetricRegistry(),
            TIMEOUT,
            ConcurrencyLimitersTest.class);

    @Test
    public void testTimeout() {
        Instant start = Instant.now();
        Thread exhauster = exhaust();
        Futures.getUnchecked(limiters.acquireLimiterInternal(KEY).acquire());
        Instant end = Instant.now();
        exhauster.interrupt();
        assertThat(Duration.between(start, end)).isGreaterThanOrEqualTo(TIMEOUT);
    }

    private Thread exhaust() {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    limiters.acquireLimiterInternal(KEY).acquire().get();
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.start();
        // wait until the other thread blocks
        while (!thread.getState().equals(Thread.State.WAITING)) {
            Thread.yield();
        }
        return thread;
    }
}
