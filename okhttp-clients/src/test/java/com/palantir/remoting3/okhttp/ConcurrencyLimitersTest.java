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

import com.netflix.concurrency.limits.Limiter;
import com.palantir.remoting3.okhttp.ConcurrencyLimiters.ConcurrencyLimiter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.Test;

public final class ConcurrencyLimitersTest {
    private final ConcurrencyLimiters limiters = new ConcurrencyLimiters();
    private final ConcurrencyLimiter limiter = limiters.limiter("limiter");

    @Test
    public void testNotBlocked() {
        assertThat(limiter.acquire().isDone()).isTrue();
    }

    // initial limit is set to 1 with our default Vegas limit
    @Test
    public void testBlocked() throws ExecutionException, InterruptedException {
        Limiter.Listener listener = limiter.acquire().get();
        Future<Limiter.Listener> blocked = limiter.acquire();
        assertThat(blocked.isDone()).isFalse();
        listener.onIgnore();
        assertThat(blocked.isDone()).isTrue();
    }

    @Test
    public void testFifo() throws ExecutionException, InterruptedException {
        Limiter.Listener listener = limiter.acquire().get();
        Future<Limiter.Listener> first = limiter.acquire();
        Future<Limiter.Listener> second = limiter.acquire();
        assertThat(first.isDone()).isFalse();
        assertThat(second.isDone()).isFalse();
        listener.onIgnore();
        assertThat(first.isDone()).isTrue();
        assertThat(second.isDone()).isFalse();
        first.get().onIgnore();
        assertThat(second.isDone()).isTrue();
    }
}
