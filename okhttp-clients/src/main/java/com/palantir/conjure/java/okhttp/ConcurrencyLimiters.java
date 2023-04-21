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
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
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

final class ConcurrencyLimiters {
    private static final SafeLogger log = SafeLoggerFactory.get(ConcurrencyLimiters.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
    private static final Void NO_CONTEXT = null;

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
        ConjureJavaClientQosMetrics metrics = ConjureJavaClientQosMetrics.of(taggedMetricRegistry);
        this.slowAcquire = metrics.requestPermitSlowAcquire();
        this.leakSuspected = metrics.requestPermitLeakSuspected();
        this.slowAcquireTagged = metrics.requestPermitSlowAcquireTagged(serviceClass.getSimpleName());
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
                /**
                 * Requests slower than this timeout are treated as failures, which reduce concurrency. Since we have
                 * plenty of long streaming requests, we set this timeout to 292.27726 years to effectively turn it off.
                 */
                .timeout(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
                /**
                 * Our initial limit is pretty conservative - only 10 concurrent requests in flight at the same time. If
                 * a client is consistently maxing out its concurrency permits, this increases additively once per
                 * second (see {@link ConjureWindowedLimit#MIN_WINDOW_TIME}.
                 */
                .initialLimit(10)
                /**
                 * We reduce concurrency _immediately_ as soon as a request fails, which can result in drastic limit
                 * reductions, e.g. starting with 30 concurrent permits, 100 failures in a row results in: 30 * 0.9^100
                 * = 0.0007 (rounded up to the minLimit of 1).
                 */
                .backoffRatio(0.9)
                /** However many failures we get, we always need at least 1 permit so we can keep trying. */
                .minLimit(1)
                /** Note that the Dispatcher in {@link OkHttpClients} has a max concurrent requests too. */
                .maxLimit(Integer.MAX_VALUE)
                .build());
    }

    private ConcurrencyLimiter newLimiter(Key limiterKey) {
        if (!useLimiter) {
            return NoOpConcurrencyLimiter.INSTANCE;
        }
        Supplier<SimpleLimiter<Void>> limiter =
                () -> SimpleLimiter.newBuilder().limit(newLimit()).build();
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
     *
     * <p>Upon a request finishing, we check if there are any waiting requests, and if there are we attempt to trigger
     * some more.
     */
    public interface ConcurrencyLimiter {
        ListenableFuture<Limiter.Listener> acquire();

        String spanName();
    }

    static final class NoOpConcurrencyLimiter implements ConcurrencyLimiter {
        private static final NoOpLimiterListener NO_OP_LIMITER_LISTENER = new NoOpLimiterListener();
        static final NoOpConcurrencyLimiter INSTANCE = new NoOpConcurrencyLimiter();

        @Override
        public ListenableFuture<Limiter.Listener> acquire() {
            return Futures.immediateFuture(NO_OP_LIMITER_LISTENER);
        }

        @Override
        public String spanName() {
            return "OkHttp: no-op-concurrency-limiter";
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
        private final ThreadWorkQueue<QueuedRequest> waitingRequests = new ThreadWorkQueue<>();

        @GuardedBy("this")
        private SimpleLimiter<Void> limiter;

        @GuardedBy("this")
        private ScheduledFuture<?> timeoutCleanup;

        private final Key limiterKey;
        private final Supplier<SimpleLimiter<Void>> limiterFactory;
        private final LeakDetector<Limiter.Listener> leakDetector = new LeakDetector<>(Limiter.Listener.class);

        DefaultConcurrencyLimiter(Key limiterKey, Supplier<SimpleLimiter<Void>> limiterFactory) {
            this.limiterKey = limiterKey;
            this.limiterFactory = limiterFactory;
            this.limiter = limiterFactory.get();
        }

        @Override
        public synchronized String spanName() {
            return String.format(
                    "OkHttp: client-side-concurrency-limiter %d/%d", limiter.getInflight(), limiter.getLimit());
        }

        @Override
        public synchronized ListenableFuture<Limiter.Listener> acquire() {
            SettableFuture<Limiter.Listener> future = SettableFuture.create();
            addSlowAcquireMarker(future);
            waitingRequests.add(new QueuedRequest(future, LeakDetector.maybeCreateStackTrace()));
            processQueue();
            return future;
        }

        synchronized void processQueue() {
            while (!waitingRequests.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug(
                            "Limit",
                            SafeArg.of("limit", limiter.getLimit()),
                            SafeArg.of("queueLength", waitingRequests.size()),
                            SafeArg.of("method", limiterKey.method()),
                            SafeArg.of("pathTemplate", limiterKey.pathTemplate()),
                            UnsafeArg.of("hostname", limiterKey.hostname()));
                }
                Optional<Limiter.Listener> maybeAcquired = limiter.acquire(NO_CONTEXT);
                if (!maybeAcquired.isPresent()) {
                    if (!timeoutScheduled()) {
                        timeoutCleanup = scheduledExecutorService.schedule(
                                this::resetLimiter, timeout.toMillis(), TimeUnit.MILLISECONDS);
                    }
                    return;
                }
                Limiter.Listener acquired = maybeAcquired.get();

                QueuedRequest request = waitingRequests.remove();

                SettableFuture<Limiter.Listener> head = request.future;
                Limiter.Listener wrapped = wrap(acquired, request.allocationStackTrace);
                boolean wasCancelled = !head.set(wrapped);
                if (wasCancelled) {
                    wrapped.onIgnore();
                }
            }

            if (timeoutScheduled()) {
                timeoutCleanup.cancel(true);
            }
        }

        private synchronized boolean timeoutScheduled() {
            return timeoutCleanup != null && !timeoutCleanup.isDone() && !timeoutCleanup.isCancelled();
        }

        private synchronized void resetLimiter() {
            log.warn(
                    "Timed out waiting to get permits for concurrency. In most cases this would indicate some kind of "
                            + "deadlock. We expect that either this is caused by either service overloading, or not "
                            + "closing response bodies (consider using the try-with-resources pattern).",
                    SafeArg.of("serviceClass", serviceClass),
                    UnsafeArg.of("hostname", limiterKey.hostname()),
                    SafeArg.of("method", limiterKey.method()),
                    SafeArg.of("pathTemplate", limiterKey.pathTemplate()),
                    SafeArg.of("timeout", timeout));
            leakSuspected.mark();
            limiter = limiterFactory.get();
            processQueue();
        }

        private void addSlowAcquireMarker(ListenableFuture<Limiter.Listener> future) {
            long start = System.nanoTime();
            Futures.addCallback(
                    future,
                    new FutureCallback<Limiter.Listener>() {
                        @Override
                        public void onSuccess(Limiter.Listener _result) {
                            long end = System.nanoTime();
                            long durationNanos = end - start;

                            // acquire calls that take less than a millisecond are considered to be successful, so we
                            // exclude
                            // them from the 'slow acquire' metric
                            if (TimeUnit.NANOSECONDS.toMillis(durationNanos) > 1) {
                                slowAcquire.update(Duration.ofNanos(durationNanos));
                                slowAcquireTagged.update(Duration.ofNanos(durationNanos));
                            }
                        }

                        @Override
                        public void onFailure(Throwable _error) {}
                    },
                    MoreExecutors.directExecutor());
        }

        private Limiter.Listener wrap(Limiter.Listener listener, Optional<RuntimeException> allocationStackTrace) {
            Limiter.Listener result = new Limiter.Listener() {
                @Override
                public void onSuccess() {
                    leakDetector.unregister(this);
                    listener.onSuccess();
                    processQueue();
                }

                @Override
                public void onIgnore() {
                    leakDetector.unregister(this);
                    listener.onIgnore();
                    processQueue();
                }

                @Override
                public void onDropped() {
                    leakDetector.unregister(this);
                    listener.onDropped();
                    processQueue();
                }
            };
            leakDetector.register(result, allocationStackTrace);
            return new AtMostOneInteractionListener(result);
        }
    }

    /** Prevent multiple interactions with the same listener from modifying state in unexpected ways. */
    @VisibleForTesting
    static final class AtMostOneInteractionListener implements Limiter.Listener {

        private final Limiter.Listener delegate;
        private boolean completed;

        AtMostOneInteractionListener(Limiter.Listener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSuccess() {
            if (canComplete()) {
                delegate.onSuccess();
            }
        }

        @Override
        public void onIgnore() {
            if (canComplete()) {
                delegate.onIgnore();
            }
        }

        @Override
        public void onDropped() {
            if (canComplete()) {
                delegate.onDropped();
            }
        }

        private boolean canComplete() {
            if (!completed) {
                completed = true;
                return true;
            }
            log.debug("Listener has already been completed");
            return false;
        }
    }

    private static final class QueuedRequest {
        private final SettableFuture<Limiter.Listener> future;
        private final Optional<RuntimeException> allocationStackTrace;

        private QueuedRequest(
                SettableFuture<Limiter.Listener> future, Optional<RuntimeException> allocationStackTrace) {
            this.future = future;
            this.allocationStackTrace = allocationStackTrace;
        }
    }
}
