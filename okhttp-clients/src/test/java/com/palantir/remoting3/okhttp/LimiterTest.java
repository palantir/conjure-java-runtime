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

import com.google.common.util.concurrent.RateLimiter;
import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.Strategy;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.netflix.concurrency.limits.limiter.DefaultLimiter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class LimiterTest {
    @Test
    public void name() {
        TestServer testServer = new TestServer();

        Runnable shutdownA = executeClientRequests("ClientA", testServer);

        sleep(5_000);

        Runnable shutdownB = executeClientRequests("ClientB", testServer);

        shutdownA.run();
        shutdownB.run();
    }

    // Make requests to the given server with a client side rate limiter enabled
    private Runnable executeClientRequests(String clientName, TestServer server) {
        DefaultLimiter<Void> clientLimiter = DefaultLimiter.newBuilder()
                .windowSize(20)
                .limit(new AimdLimit(20, 0.8))
                .build(new RateLimiterStrategy(clientName));

        ExecutorService clientThreads = Executors.newFixedThreadPool(10);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int i = 0; i < 1_000; i++) {
            clientThreads.submit(() -> {
                Limiter.Listener listener = clientLimiter.acquire(null).orElseThrow(IllegalStateException::new);

                if (server.execute()) {
                    success.incrementAndGet();
                    listener.onSuccess();
                } else {
                    failure.incrementAndGet();
                    listener.onDropped();
                }
            });
        }

        // delay shutdown so more clients can be introduced concurrently
        return () -> {
            clientThreads.shutdown();
            try {
                clientThreads.awaitTermination(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            System.out.println("Successes: " + success.get());
            System.out.println("Failures: " + failure.get());
        };
    }

    // Mimic rate limiting done in a server where successful requests take longer than rate limited requests.
    private static final class TestServer {
        private final RateLimiter serverLimiter = RateLimiter.create(20);

        public boolean execute() {
            if (serverLimiter.tryAcquire()) {
                sleep(10);
                return true;
            } else {
                sleep(1);
                return false;
            }
        }
    }

    /**
     * Blocks on acquiring a permit from a local {@link RateLimiter}.
     */
    private static final class RateLimiterStrategy implements Strategy<Void> {
        private final RateLimiter limiter = RateLimiter.create(1);
        private final AtomicInteger inFlight = new AtomicInteger(0);
        private final String clientName;

        RateLimiterStrategy(String clientName) {
            this.clientName = clientName;
        }

        @Override
        public Token tryAcquire(Void context) {
            limiter.acquire();
            return Token.newAcquired(inFlight.incrementAndGet(), inFlight::decrementAndGet);
        }

        @Override
        public void setLimit(int limit) {
            System.out.println(clientName + " limit: " + limit);
            limiter.setRate(limit);
        }
    }

    /**
     * Same as {@link AIMDLimit} except increases the limit whenever there are no failures in the given window.
     */
    private class AimdLimit implements Limit {
        private final double backoffRatio;
        private int limit;

        AimdLimit(int initLimit, double backoffRatio) {
            this.limit = initLimit;
            this.backoffRatio = backoffRatio;
        }

        @Override
        public int getLimit() {
            return limit;
        }

        @Override
        public void update(SampleWindow sample) {
            if (sample.didDrop()) {
                limit = Math.max(1, Math.min(limit - 1, (int) (limit * backoffRatio)));
            } else {
                limit = limit + 1;
            }
        }
    }

    private static void sleep(long sleepMillis) {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
