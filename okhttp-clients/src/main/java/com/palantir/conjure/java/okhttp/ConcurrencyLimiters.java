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
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.VegasLimit;
import com.netflix.concurrency.limits.limiter.BlockingLimiter;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import com.palantir.logsafe.SafeArg;
import com.palantir.tracing.CloseableTracer;
import com.palantir.tracing.okhttp3.OkhttpTraceInterceptor;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("DesignForExtension")
class ConcurrencyLimiters {
    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimiters.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
    private static final Void NO_CONTEXT = null;
    private static final String FALLBACK = "";
    private static final MetricName SLOW_ACQUIRE =
            MetricName.builder().safeName("conjure-java-client.qos.request-permit.slow-acquire").build();
    private static final MetricName LEAK_SUSPECTED =
            MetricName.builder().safeName("conjure-java-client.qos.request-permit.leak-suspected").build();

    private final Timer slowAcquire;
    private final Meter leakSuspected;
    private final ConcurrentMap<String, Limiter<Void>> limiters = new ConcurrentHashMap<>();
    private final Duration timeout;
    private final Class<?> serviceClass;

    @VisibleForTesting
    ConcurrencyLimiters(TaggedMetricRegistry taggedMetricRegistry, Duration timeout, Class<?> serviceClass) {
        this.slowAcquire = taggedMetricRegistry.timer(SLOW_ACQUIRE);
        this.leakSuspected = taggedMetricRegistry.meter(LEAK_SUSPECTED);
        this.timeout = timeout;
        this.serviceClass = serviceClass;
    }

    ConcurrencyLimiters(TaggedMetricRegistry taggedMetricRegistry, Class<?> serviceClass) {
        this(taggedMetricRegistry, DEFAULT_TIMEOUT, serviceClass);
    }

    /**
     * Blocks until the request should be allowed to proceed.
     * Caller must notify the listener to release the permit.
     */
    @SuppressWarnings("unused")
    Limiter.Listener acquireLimiter(Request request) {
        long start = System.nanoTime();
        try (CloseableTracer unused = CloseableTracer.startSpan("acquireLimiter")) {
            return acquireLimiterInternal(limiterKey(request), 0);
        } finally {
            long end = System.nanoTime();
            long durationNanos = end - start;

            // acquire calls that take less than a millisecond are considered to be successful, so we exclude
            // them from the 'slow acquire' metric
            if (TimeUnit.NANOSECONDS.toMillis(durationNanos) > 1) {
                slowAcquire.update(durationNanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    @VisibleForTesting
    Limiter.Listener acquireLimiterInternal(String limiterKey, int attemptsSoFar) {
        Limiter<Void> limiter = limiters.computeIfAbsent(limiterKey, key -> newLimiter());
        Optional<Limiter.Listener> listener = limiter.acquire(NO_CONTEXT);

        if (listener.isPresent()) {
            if (attemptsSoFar > 0) {
                log.info("Eventually acquired concurrency permit",
                        SafeArg.of("serviceClass", serviceClass),
                        SafeArg.of("limiterKey", limiterKey),
                        SafeArg.of("attemptsSoFar", attemptsSoFar + 1));
            }

            return listener.get();
        } else {
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("Thread was interrupted");
            }
            log.warn("Timed out waiting to get permits for concurrency. In most cases this would indicate "
                            + "some kind of deadlock. We expect that either this is caused by not closing response "
                            + "bodies (there should be OkHttp log lines indicating this), or service overloading.",
                    SafeArg.of("serviceClass", serviceClass),
                    SafeArg.of("limiterKey", limiterKey),
                    SafeArg.of("attemptsSoFar", attemptsSoFar + 1),
                    SafeArg.of("timeout", timeout));
            leakSuspected.mark();
            limiters.replace(limiterKey, limiter, newLimiter());
            return acquireLimiterInternal(limiterKey, attemptsSoFar + 1);
        }
    }

    private Limiter<Void> newLimiter() {
        Limiter<Void> limiter = SimpleLimiter.newBuilder()
                .limit(new ConjureWindowedLimit(VegasLimit.newDefault()))
                .build();
        return BlockingLimiter.wrap(limiter, timeout);
    }

    private static String limiterKey(Request request) {
        String pathTemplate = request.header(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER);
        if (pathTemplate == null) {
            return FALLBACK;
        } else {
            return request.method() + " " + pathTemplate;
        }
    }
}
