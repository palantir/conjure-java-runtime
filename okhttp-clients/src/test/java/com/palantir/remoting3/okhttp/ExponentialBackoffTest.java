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

package com.palantir.remoting3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.concurrency.limits.Limiter;
import com.palantir.remoting3.okhttp.ConcurrencyLimiters.ConcurrencyLimiter;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import jersey.repackaged.com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class ExponentialBackoffTest {
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    @Test
    public void testNoRetry() {
        Random random = mock(Random.class);
        ExponentialBackoff backoff = new ExponentialBackoff(0, ONE_SECOND, random);

        assertThat(backoff.nextBackoff()).isEmpty();
    }

    @Test
    public void testRetriesCorrectNumberOfTimesAndFindsRandomBackoffWithInExponentialInterval() {
        Random random = mock(Random.class);
        ExponentialBackoff backoff = new ExponentialBackoff(3, ONE_SECOND, random);

        when(random.nextDouble()).thenReturn(1.0);
        assertThat(backoff.nextBackoff()).contains(ONE_SECOND.multipliedBy(2));

        when(random.nextDouble()).thenReturn(1.0);
        assertThat(backoff.nextBackoff()).contains(ONE_SECOND.multipliedBy(4));

        when(random.nextDouble()).thenReturn(0.5);
        assertThat(backoff.nextBackoff()).contains(ONE_SECOND.multipliedBy(4 /* 8 * 0.5 (exp * jitter), see above */));

        assertThat(backoff.nextBackoff()).isEmpty();
    }

    @Test
    public void testBackingOff() throws ExecutionException, InterruptedException {
        int numThreads = 160;
        int numRequestsPerSecond = 40;
        int numRetries = 3;
        ListeningExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(numThreads,
                new ThreadFactoryBuilder().setNameFormat("test-name-%d").build()));
        RateLimiter rateLimiter = RateLimiter.create(numRequestsPerSecond);

        ConcurrencyLimiter limiter = new ConcurrencyLimiters().limiter("");
        Meter meter = new Meter();
        Histogram backoffHistogram = new Histogram(new ExponentiallyDecayingReservoir());

        List<ListenableFuture<?>> futures = IntStream.range(0, numThreads).mapToObj(x -> executorService.submit(new Runnable() {
            ExponentialBackoff backoff;
            int backoffIndex = 0;

            @Override
            public void run() {
                for (int i = 0; i < 1001;) {
                    Limiter.Listener listener = Futures.getUnchecked(limiter.acquire());
                    //System.out.println(i);
                    boolean gotRateLimited = !rateLimiter.tryAcquire();
                    if (!gotRateLimited) {
                        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
                        listener.onSuccess();
                        meter.mark();
                        if (i++ % 1 == 0) {
                            System.out.println("i " + i + " avg " + meter.getMeanRate() + " avgbackoffs " + backoffHistogram.getSnapshot().getMean());
                        }
                        backoffHistogram.update(backoffIndex);
                        backoff = null;
                        backoffIndex = 0;
                    } else {
                        initializeBackoff();
                        Optional<Duration> sleep = backoff.nextBackoff();
                        if (!sleep.isPresent()) {
                            throw new RuntimeException("i " + i);
                        } else {
                            System.out.println("Backoff: " + ++backoffIndex);
                            Uninterruptibles.sleepUninterruptibly(sleep.get().toMillis(), TimeUnit.MILLISECONDS);
                        }
                        listener.onDropped();
                    }
                }
                System.out.println("done");
            }

            private void initializeBackoff() {
                if (backoff != null) {
                    return;
                }
                backoff = new ExponentialBackoff(numRetries, Duration.ofMillis(250), ThreadLocalRandom.current());
            }
        })).collect(Collectors.toList());

        Futures.allAsList(futures).get();
    }
}
