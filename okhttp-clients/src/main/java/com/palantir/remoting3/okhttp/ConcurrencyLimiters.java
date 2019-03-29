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

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting3.tracing.okhttp3.OkhttpTraceInterceptor;
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

    @VisibleForTesting
    ConcurrencyLimiters(TaggedMetricRegistry taggedMetricRegistry, Duration timeout) {
        this.slowAcquire = taggedMetricRegistry.timer(SLOW_ACQUIRE);
        this.leakSuspected = taggedMetricRegistry.meter(LEAK_SUSPECTED);
        this.timeout = timeout;
    }

    ConcurrencyLimiters(TaggedMetricRegistry taggedMetricRegistry) {
        this(taggedMetricRegistry, DEFAULT_TIMEOUT);
    }

    /**
     * Blocks until the request should be allowed to proceed.
     * Caller must notify the listener to release the permit.
     */
    Limiter.Listener acquireLimiter(Request request) {
        return acquireLimiter(limiterKey(request));
    }

    @VisibleForTesting
    Limiter.Listener acquireLimiter(String name) {
        Limiter<Void> limiter = limiters.computeIfAbsent(name, key -> newLimiter());
        Optional<Limiter.Listener> listener = limiter.acquire(NO_CONTEXT);
        return listener.orElseGet(() -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("Thread was interrupted");
            }
            log.warn("Timed out waiting to get permits for concurrency. In most cases this would indicate "
                            + "some kind of deadlock. We expect that either this is caused by not closing response "
                            + "bodies (there should be OkHttp log lines indicating this), or service overloading.",
                    SafeArg.of("timeout", timeout));
            leakSuspected.mark();
            limiters.replace(name, limiter, newLimiter());
            return acquireLimiter(name);
        });
    }

    private Limiter<Void> newLimiter() {
        Limiter<Void> limiter = new InstrumentedLimiter(SimpleLimiter.newBuilder()
                .limit(new RemotingWindowedLimit(AIMDLimit.newBuilder()
                        .timeout(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
                        .build()))
                .build(), slowAcquire);
        return RemotingBlockingLimiter.wrap(limiter, timeout);
    }

    private static String limiterKey(Request request) {
        String pathTemplate = request.header(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER);
        if (pathTemplate == null) {
            return FALLBACK;
        } else {
            return request.method() + " " + pathTemplate;
        }
    }

    private static final class InstrumentedLimiter implements Limiter<Void> {
        private final Limiter<Void> delegate;
        private final Timer timer;

        private InstrumentedLimiter(Limiter<Void> delegate, Timer timer) {
            this.delegate = delegate;
            this.timer = timer;
        }

        @Override
        public Optional<Listener> acquire(Void context) {
            long start = System.nanoTime();
            try {
                return delegate.acquire(context);
            } finally {
                long end = System.nanoTime();
                long durationNanos = end - start;
                if (TimeUnit.NANOSECONDS.toMillis(durationNanos) > 1) {
                    timer.update(durationNanos, TimeUnit.NANOSECONDS);
                }
            }
        }
    }
}
