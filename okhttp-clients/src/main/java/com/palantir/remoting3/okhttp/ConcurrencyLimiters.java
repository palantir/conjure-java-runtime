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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.netflix.concurrency.limits.limit.TracingLimitDecorator;
import com.netflix.concurrency.limits.limiter.DefaultLimiter;
import com.netflix.concurrency.limits.strategy.SimpleStrategy;
import com.palantir.remoting3.tracing.okhttp3.OkhttpTraceInterceptor;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * success. If this is not done, throughput will be negatively affected. We attempt to eventually recover to avoid a
 * total deadlock, but this is not guaranteed.
 */
final class ConcurrencyLimiters {
    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimiters.class);
    private static final String FALLBACK = "";

    // If a request is never marked as complete and is thrown away, recover on the next GC instead of deadlocking
    private static final Map<Limiter.Listener, Runnable> activeListeners = CacheBuilder.newBuilder()
            .weakKeys()
            .<Limiter.Listener, Runnable>removalListener(notification -> {
                if (notification.getCause().equals(RemovalCause.COLLECTED)) {
                    log.warn("Concurrency limiter was leaked."
                            + " This implies a remoting bug or classpath issue, and may cause degraded performance");
                    notification.getValue().run();
                }
            })
            .build()
            .asMap();

    private final ConcurrentMap<String, ConcurrencyLimiter> limiters = new ConcurrentHashMap<>();

    @VisibleForTesting
    ConcurrencyLimiter limiter(String name) {
        return limiters.computeIfAbsent(name, key -> new ConcurrencyLimiter(DefaultLimiter.newBuilder()
                .limit(TracingLimitDecorator.wrap(AIMDLimit.newBuilder().initialLimit(1).build()))
                .build(new SimpleStrategy<>())));
    }

    ConcurrencyLimiter limiter(Request request) {
        final String limiterKey;
        String pathTemplate = request.header(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER);
        if (pathTemplate == null) {
            limiterKey = FALLBACK;
        } else {
            limiterKey = request.method() + " " + pathTemplate;
        }
        return limiter(limiterKey);
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
        private final Queue<SettableFuture<Limiter.Listener>> waitingRequests = new LinkedBlockingQueue<>();
        private final Limiter<Void> limiter;

        @VisibleForTesting
        ConcurrencyLimiter(Limiter<Void> limiter) {
            this.limiter = limiter;
        }

        public ListenableFuture<Limiter.Listener> acquire() {
            SettableFuture<Limiter.Listener> future = SettableFuture.create();
            waitingRequests.add(future);
            processQueue();
            return future;
        }

        private void processQueue() {
            while (!waitingRequests.isEmpty()) {
                Optional<Limiter.Listener> maybeAcquired = limiter.acquire(null);
                if (!maybeAcquired.isPresent()) {
                    return;
                }
                Limiter.Listener acquired = maybeAcquired.get();
                SettableFuture<Limiter.Listener> head = waitingRequests.poll();
                if (head == null) {
                    acquired.onIgnore();
                } else {
                    head.set(wrap(acquired));
                }
            }
        }

        private Limiter.Listener wrap(Limiter.Listener listener) {
            Limiter.Listener res = new Limiter.Listener() {
                @Override
                public void onSuccess() {
                    listener.onSuccess();
                    activeListeners.remove(this);
                    processQueue();
                }

                @Override
                public void onIgnore() {
                    listener.onIgnore();
                    activeListeners.remove(this);
                    processQueue();
                }

                @Override
                public void onDropped() {
                    listener.onDropped();
                    activeListeners.remove(this);
                    processQueue();
                }
            };
            activeListeners.put(res, () -> {
                listener.onIgnore();
                processQueue();
            });
            return res;
        }

    }
}
