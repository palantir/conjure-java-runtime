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

package com.palantir.remoting3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

public final class ConcurrencyLimitersTest {
    private static final String KEY = "";
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private final ConcurrencyLimiters limiters = new ConcurrencyLimiters(new DefaultTaggedMetricRegistry(), TIMEOUT);

    @Test
    public void testTimeout() {
        Instant start = Instant.now();
        Thread exhauster = exhaust();
        limiters.limiter(KEY);
        Instant end = Instant.now();
        exhauster.interrupt();
        assertThat(Duration.between(start, end)).isGreaterThan(TIMEOUT);
    }

    private Thread exhaust() {
        Thread thread = new Thread(() -> {
            while (true) {
                limiters.limiter(KEY);
            }
        });
        thread.start();
        // wait until the other thread blocks
        while (!thread.getState().equals(Thread.State.TIMED_WAITING)) {
            Thread.yield();
        }
        return thread;
    }
}
