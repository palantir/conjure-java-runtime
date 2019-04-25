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

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import com.palantir.logsafe.SafeArg;
import com.palantir.tracing.okhttp3.OkhttpTraceInterceptor;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.concurrent.GuardedBy;
import okhttp3.Request;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConcurrencyLimiters {
    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimiters.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
    private static final Void NO_CONTEXT = null;
    private static final MetricName SLOW_ACQUIRE =
            MetricName.builder().safeName("conjure-java-client.qos.request-permit.slow-acquire").build();
    private static final MetricName LEAK_SUSPECTED =
            MetricName.builder().safeName("conjure-java-client.qos.request-permit.leak-suspected").build();
    private static final String SLOW_ACQUIRE_TAGGED = "conjure-java-client.qos.request-permit.slow-acquire-tagged";

    private final Timer slowAcquire;
    private final Timer slowAcquireTagged;
    private final Meter leakSuspected;
    private final ConcurrentMap<Key, ConcurrencyLimiter> limiters = new ConcurrentHashMap<>();
    private final Duration timeout;
    private final Class<?> serviceClass;
    private final ScheduledExecutorService scheduledExecutorService;
    private final boolean useLimiter;

    @VisibleForTesting
    ConcurrencyLimiters(
            ScheduledExecutorService scheduledExecutorService,
            TaggedMetricRegistry taggedMetricRegistry,
            Duration timeout,
            Class<?> serviceClass,
            boolean useLimiter) {
        this.slowAcquire = taggedMetricRegistry.timer(SLOW_ACQUIRE);
        this.leakSuspected = taggedMetricRegistry.meter(LEAK_SUSPECTED);
        this.slowAcquireTagged = taggedMetricRegistry.timer(generateMetricNameWithServiceName(SLOW_ACQUIRE_TAGGED,
                serviceClass));
        this.timeout = timeout;
        this.serviceClass = serviceClass;
        this.scheduledExecutorService = scheduledExecutorService;
        this.useLimiter = useLimiter;
    }

    ConcurrencyLimiters(
            ScheduledExecutorService scheduledExecutorService,
            TaggedMetricRegistry taggedMetricRegistry,
            Class<?> serviceClass,
            boolean useLimiter) {
        this(scheduledExecutorService, taggedMetricRegistry, DEFAULT_TIMEOUT, serviceClass, useLimiter);
    }

    /**
     * Returns async limiter that users can subscribe to be notified when limit permit has been granted. Caller must
     * notify the listener to release the permit.
     */
    ConcurrencyLimiter acquireLimiter(Request request) {
        return acquireLimiterInternal(limiterKey(request));
    }

    @VisibleForTesting
    ConcurrencyLimiter acquireLimiterInternal(Key limiterKey) {
        return limiters.computeIfAbsent(limiterKey, this::newLimiter);
    }

    @VisibleForTesting
    Limit newLimit() {
        return new ConjureWindowedLimit(AIMDLimit.newBuilder()
                .timeout(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
                .build());
    }

    private MetricName generateMetricNameWithServiceName(String name, Class<?> service) {
        return MetricName.builder()
                .safeName(name)
                .putSafeTags("serviceClass", service.getSimpleName())
                .build();
    }

    private ConcurrencyLimiter newLimiter(Key limiterKey) {
        if (!useLimiter) {
            return NoOpConcurrencyLimiter.INSTANCE;
        }
        Supplier<Limiter<Void>> limiter = () -> SimpleLimiter.newBuilder().limit(newLimit()).build();
        return new DefaultConcurrencyLimiter(limiterKey, limiter);
    }

    private Key limiterKey(Request request) {
        String pathTemplate = request.header(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER);
        if (pathTemplate == null) {
            return ImmutableKey.builder().hostname(request.url().host()).build();
        } else {
            return ImmutableKey.builder()
                    .hostname(request.url().host())
                    .method(request.method())
                    .pathTemplate(pathTemplate)
                    .build();
        }
    }

    @Value.Immutable
    interface Key {
        String hostname();
        Optional<String> method();
        Optional<String> pathTemplate();
    }

    /**
     * The Netflix library provides either a blocking approach or a non-blocking approach which might say you can't be
     * scheduled at this time. All of our HTTP calls are asynchronous, so we really want to get a
     * {@link ListenableFuture} that we can add a callback to. This class then is a translation of
     * {@link com.netflix.concurrency.limits.limiter.BlockingLimiter} to be asynchronous, maintaining a queue of
     * currently waiting requests.
     * <p>
     * Upon a request finishing, we check if there are any waiting requests, and if there are we attempt to trigger some
     * more.
     */
    public interface ConcurrencyLimiter {
        ListenableFuture<Limiter.Listener> acquire();
    }

    static final class NoOpConcurrencyLimiter implements ConcurrencyLimiter {
        private static final NoOpLimiterListener NO_OP_LIMITER_LISTENER = new NoOpLimiterListener();
        static final NoOpConcurrencyLimiter INSTANCE = new NoOpConcurrencyLimiter();

        @Override
        public ListenableFuture<Limiter.Listener> acquire() {
            return Futures.immediateFuture(NO_OP_LIMITER_LISTENER);
        }

        static final class NoOpLimiterListener implements Limiter.Listener {
            @Override
            public void onSuccess() {}
            @Override
            public void onIgnore() {}
            @Override
            public void onDropped() {}
        }
    }

    final class DefaultConcurrencyLimiter implements ConcurrencyLimiter {
        @GuardedBy("this")
        private final ThreadWorkQueue<SettableFuture<Limiter.Listener>> waitingRequests = new ThreadWorkQueue<>();
        @GuardedBy("this")
        private Limiter<Void> limiter;
        @GuardedBy("this")
        private ScheduledFuture<?> timeoutCleanup;
        private final Key limiterKey;
        private final Supplier<Limiter<Void>> limiterFactory;

        DefaultConcurrencyLimiter(Key limiterKey, Supplier<Limiter<Void>> limiterFactory) {
            this.limiterKey = limiterKey;
            this.limiterFactory = limiterFactory;
            this.limiter = limiterFactory.get();
        }

        @Override
        public synchronized ListenableFuture<Limiter.Listener> acquire() {
            SettableFuture<Limiter.Listener> future = SettableFuture.create();
            addSlowAcquireMarker(future);
            waitingRequests.add(future);
            processQueue();
            return future;
        }

        synchronized void processQueue() {
            while (!waitingRequests.isEmpty()) {
                Optional<Limiter.Listener> maybeAcquired = limiter.acquire(NO_CONTEXT);
                if (!maybeAcquired.isPresent()) {
                    if (!timeoutScheduled()) {
                        timeoutCleanup = scheduledExecutorService.schedule(
                                this::resetLimiter, timeout.toMillis(), TimeUnit.MILLISECONDS);
                    }
                    return;
                }
                Limiter.Listener acquired = maybeAcquired.get();
                SettableFuture<Limiter.Listener> head = waitingRequests.remove();
                head.set(wrap(acquired));
            }

            if (timeoutScheduled()) {
                timeoutCleanup.cancel(true);
            }
        }

        private synchronized boolean timeoutScheduled() {
            return timeoutCleanup != null && !timeoutCleanup.isDone() && !timeoutCleanup.isCancelled();
        }

        private synchronized void resetLimiter() {
            log.warn("Timed out waiting to get permits for concurrency. In most cases this would indicate some kind of "
                            + "deadlock. We expect that either this is caused by not closing response bodies "
                            + "(there should be OkHttp log lines indicating this), or service overloading.",
                    SafeArg.of("serviceClass", serviceClass),
                    SafeArg.of("limiterKey", limiterKey),
                    SafeArg.of("timeout", timeout));
            leakSuspected.mark();
            limiter = limiterFactory.get();
            processQueue();
        }

        private void addSlowAcquireMarker(ListenableFuture<Limiter.Listener> future) {
            long start = System.nanoTime();
            Futures.addCallback(future, new FutureCallback<Limiter.Listener>() {
                @Override
                public void onSuccess(Limiter.Listener result) {
                    long end = System.nanoTime();
                    long durationNanos = end - start;

                    // acquire calls that take less than a millisecond are considered to be successful, so we exclude
                    // them from the 'slow acquire' metric
                    if (TimeUnit.NANOSECONDS.toMillis(durationNanos) > 1) {
                        slowAcquire.update(durationNanos, TimeUnit.NANOSECONDS);
                        slowAcquireTagged.update(durationNanos, TimeUnit.NANOSECONDS);
                    }
                }

                @Override
                public void onFailure(Throwable error) {

                }
            }, MoreExecutors.directExecutor());
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
