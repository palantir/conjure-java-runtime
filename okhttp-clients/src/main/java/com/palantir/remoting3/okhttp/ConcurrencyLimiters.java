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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.TracingLimitDecorator;
import com.netflix.concurrency.limits.limit.VegasLimit;
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

final class ConcurrencyLimiters {
    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimiters.class);
    private static final String FALLBACK = "";
    private final ConcurrentMap<String, AsyncLimiter> limiters = new ConcurrentHashMap<>();

    private final Map<Limiter.Listener, Runnable> activeListeners = CacheBuilder.newBuilder()
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

    @VisibleForTesting
    AsyncLimiter limiter(String name) {
        return limiters.computeIfAbsent(name, key ->
                new AsyncLimiter(activeListeners, DefaultLimiter.newBuilder()
                        .limit(TracingLimitDecorator.wrap(VegasLimit.newBuilder().initialLimit(1).build()))
                        .build(new SimpleStrategy<>())));
    }

    public AsyncLimiter limiter(Request request) {
        final String limiterKey;
        String pathTemplate = request.header(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER);
        if (pathTemplate == null) {
            limiterKey = FALLBACK;
        } else {
            limiterKey = request.method() + " " + pathTemplate;
        }
        return limiter(limiterKey);
    }

    static final class AsyncLimiter {
        private final Map<Limiter.Listener, Runnable> activeListeners;
        private final Queue<SettableFuture<Limiter.Listener>> waitingRequests = new LinkedBlockingQueue<>();
        private final Limiter<Void> limiter;

        public AsyncLimiter(
                Map<Limiter.Listener, Runnable> activeListeners,
                Limiter<Void> limiter) {
            this.activeListeners = activeListeners;
            this.limiter = limiter;
        }

        public ListenableFuture<Limiter.Listener> acquire() {
            Optional<Limiter.Listener> maybeListener = limiter.acquire(null);
            if (maybeListener.isPresent()) {
                return Futures.immediateFuture(wrap(activeListeners, maybeListener.get()));
            }
            SettableFuture<Limiter.Listener> future = SettableFuture.create();
            waitingRequests.add(future);
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
                    head.set(acquired);
                }
            }
        }

        private Limiter.Listener wrap(
                Map<Limiter.Listener, Runnable> activeListeners, Limiter.Listener listener) {
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
