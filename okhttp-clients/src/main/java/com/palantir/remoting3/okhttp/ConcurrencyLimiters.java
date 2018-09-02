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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.TracingLimitDecorator;
import com.netflix.concurrency.limits.limit.VegasLimit;
import com.netflix.concurrency.limits.limiter.DefaultLimiter;
import com.netflix.concurrency.limits.strategy.SimpleStrategy;
import com.palantir.remoting3.tracing.okhttp3.OkhttpTraceInterceptor;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.concurrent.GuardedBy;
import okhttp3.Request;

/**
 * Flow control in Conjure is a collaborative effort between servers and clients. Servers advertise an overloaded state
 * via 429/503 responses, and clients throttle the number of requests that they send concurrently as a response to this.
 * The latter is implemented as a combination of two techniques, yielding a mechanism similar to flow control in TCP/IP.
 * <ol>
 *     <li>
 *         Clients use the frequency of 429/503 responses (as well as the request latency) to determine an estimate
 *         for the number of permissible concurrent requests
 *    </li>
 *     <li>
 *         Each such request gets scheduled according to an exponential backoff algorithm.
 *     </li>
 * </ol>
 * <p>
 * This class provides an asynchronous implementation of Netflix's
 * <a href="https://github.com/Netflix/concurrency-limits/">concurrency-limits</a> library for determining the
 * above mentioned concurrency estimates.
 * <p>
 * In order to use this class, one should acquire a Limiter for their request, which returns a future. once the Future
 * is completed, the caller can assume that the request is schedulable. After the request completes, the caller
 * <b>must</b> call one of the methods on {@link Limiter.Listener} in order to provide feedback about the request's
 * success. If this is not done, a deadlock could result.
 */
final class ConcurrencyLimiters {
    private static final Void NO_CONTEXT = null;
    private static final String FALLBACK = "";

    private final ConcurrentMap<String, ConcurrencyLimiter> limiters = new ConcurrentHashMap<>();
    private final int initialLimit;

    @VisibleForTesting
    ConcurrencyLimiters(int initialLimit) {
        this.initialLimit = initialLimit;
    }

    public ConcurrencyLimiters() {
        this(1);
    }

    @VisibleForTesting
    ConcurrencyLimiter limiter(String name) {
        return limiters.computeIfAbsent(name, key -> new ConcurrencyLimiter(DefaultLimiter.newBuilder()
                //.windowSize(10)
                //.minWindowTime(100, TimeUnit.MILLISECONDS)
                .limit(TracingLimitDecorator.wrap(VegasLimit.newBuilder()
                        .initialLimit(1)
                        .decrease(x -> x / 2)
                        .build()))
                .build(new SimpleStrategy<>())));
    }

    ConcurrencyLimiter limiter(Request request) {
        return limiter(limiterKey(request));
    }

    private static String limiterKey(Request request) {
        String pathTemplate = request.header(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER);
        if (pathTemplate == null) {
            return FALLBACK;
        } else {
            return request.method() + " " + pathTemplate;
        }
    }

    /**
     * The Netflix library provides either a blocking approach or a non-blocking approach which might say
     * you can't be scheduled at this time. All of our HTTP calls are asynchronous, so we really want to get
     * a {@link ListenableFuture} that we can add a callback to. This class then is a translation of
     * {@link com.netflix.concurrency.limits.limiter.BlockingLimiter} to be asynchronous, maintaining a queue
     * of currently waiting requests.
     * <p>
     * Upon a request finishing, we check if there are any waiting requests, and if there are we attempt to trigger
     * some more.
     */
    static final class ConcurrencyLimiter {
        @GuardedBy("this")
        private final Queue<SettableFuture<Limiter.Listener>> waitingRequests = new ArrayDeque<>();
        private final Limiter<Void> limiter;

        @VisibleForTesting
        ConcurrencyLimiter(Limiter<Void> limiter) {
            this.limiter = limiter;
        }

        synchronized ListenableFuture<Limiter.Listener> acquire() {
            SettableFuture<Limiter.Listener> future = SettableFuture.create();
            waitingRequests.add(future);
            processQueue();
            return future;
        }

        private synchronized void processQueue() {
            while (!waitingRequests.isEmpty()) {
                Optional<Limiter.Listener> maybeAcquired = limiter.acquire(NO_CONTEXT);
                if (!maybeAcquired.isPresent()) {
                    return;
                }
                Limiter.Listener acquired = maybeAcquired.get();
                SettableFuture<Limiter.Listener> head = waitingRequests.remove();
                head.set(wrap(acquired));
            }
        }

        private Limiter.Listener wrap(Limiter.Listener listener) {
            return new Limiter.Listener() {
                @Override
                public void onSuccess() {
                    listener.onSuccess();
                    processQueue();
                }

                @Override
                public void onIgnore() {
                    listener.onIgnore();
                    processQueue();
                }

                @Override
                public void onDropped() {
                    listener.onDropped();
                    processQueue();
                }
            };
        }

    }
}
