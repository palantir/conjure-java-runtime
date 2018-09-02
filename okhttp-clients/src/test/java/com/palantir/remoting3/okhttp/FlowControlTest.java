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

import com.codahale.metrics.Meter;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.RateLimiter;
import com.netflix.concurrency.limits.Limiter;
import com.palantir.remoting3.okhttp.ConcurrencyLimiters.ConcurrencyLimiter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a simulation of the flow control primitives used by this library, in order to allow the developer
 * to try different strategies.
 * <p>
 * It is run in CI, but only to prevent code breakages - this is in general an expensive test which should be run
 * as a dev tool.
 */
public final class FlowControlTest {
    private static final Logger log = LoggerFactory.getLogger(FlowControlTest.class);
    private static final Duration GRACE = Duration.ofMinutes(2);
    private static final int REQUESTS_PER_THREAD = System.getenv("CI") == null ? 1000 : 1;
    private static final ConcurrencyLimiters limiters = new ConcurrencyLimiters();
    private static ListeningExecutorService executorService;

    private final ConcurrencyLimiter limiter = limiters.limiter(UUID.randomUUID().toString());

    @BeforeClass
    public static void beforeClass() {
        executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    }

    @AfterClass
    public static void afterClass() {
        executorService.shutdown();
    }

    @Test
    public void test16ThreadsRateLimit20() throws ExecutionException, InterruptedException {
        Meter rate = new Meter();
        int rateLimit = 20;
        List<ListenableFuture<?>> tasks = createWorkers(rate, 21, rateLimit, Duration.ofMillis(50))
                .map(executorService::submit)
                .collect(Collectors.toList());
        ListenableFuture<?> task = Futures.allAsList(tasks);
        Instant start = Instant.now();
        while (!task.isDone()) {
            sleep(1000);
            log.info("Average rate is {}, 1 minute rate is {}", rate.getMeanRate(), rate.getOneMinuteRate());
            if (Duration.between(start, Instant.now()).compareTo(GRACE) > 0) {
                assertThat(rate.getMeanRate()).isGreaterThan(0.75 * rateLimit);
            }
        }
        task.get();
    }

    private Stream<Worker> createWorkers(Meter rate, int numThreads, int rateLimit, Duration delay) {
        RateLimiter rateLimiter = RateLimiter.create(rateLimit);
        return IntStream.range(0, numThreads)
                .mapToObj(unused -> new Worker(
                        () -> new ExponentialBackoff(4, Duration.ofMillis(250), ThreadLocalRandom.current()),
                        limiter,
                        delay,
                        rateLimiter,
                        rate));
    }

    private static class Worker implements Runnable {
        private final Supplier<BackoffStrategy> backoffFactory;
        private final ConcurrencyLimiter limiter;
        private final Duration successDuration;
        private final RateLimiter rateLimiter;
        private final Meter meter;

        private BackoffStrategy backoff;

        private Worker(
                Supplier<BackoffStrategy> backoffFactory,
                ConcurrencyLimiter limiter,
                Duration successDuration,
                RateLimiter rateLimiter,
                Meter meter) {
            this.backoffFactory = backoffFactory;
            this.limiter = limiter;
            this.successDuration = successDuration;
            this.rateLimiter = rateLimiter;
            this.meter = meter;
        }

        @Override
        public void run() {
            for (int i = 0; i < REQUESTS_PER_THREAD;) {
                Limiter.Listener listener = Futures.getUnchecked(limiter.acquire());
                boolean gotRateLimited = !rateLimiter.tryAcquire();
                if (!gotRateLimited) {
                    meter.mark();
                    sleep(successDuration.toMillis());
                    listener.onSuccess();
                    backoff = null;
                    i++;
                } else {
                    initializeBackoff();
                    Optional<Duration> sleep = backoff.nextBackoff();
                    if (!sleep.isPresent()) {
                        listener.onIgnore();
                        throw new RuntimeException("Failed on request " + i);
                    } else {
                        sleep(sleep.get().toMillis());
                        listener.onDropped();
                    }
                }
            }
        }

        private void initializeBackoff() {
            if (backoff != null) {
                return;
            }
            backoff = backoffFactory.get();
        }
    }

    private static void sleep(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
