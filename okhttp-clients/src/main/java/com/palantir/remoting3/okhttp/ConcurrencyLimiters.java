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
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.VegasLimit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting3.tracing.okhttp3.OkhttpTraceInterceptor;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConcurrencyLimiters {
    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimiters.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
    private static final Void NO_CONTEXT = null;
    private static final String FALLBACK = "";

    private final ConcurrentMap<String, Limiter<Void>> limiters = new ConcurrentHashMap<>();

    Limiter.Listener limiter(Request request) {
        return limiter(limiterKey(request));
    }

    @VisibleForTesting
    Limiter.Listener limiter(String name) {
        Limiter<Void> limiter = limiters.computeIfAbsent(name, key -> newLimiter());
        Optional<Limiter.Listener> listener = limiter.acquire(NO_CONTEXT);
        return listener.orElseGet(() -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("Thread was interrupted");
            }
            log.warn("Timed out waiting to get permits for concurrency. In most cases this would indicate "
                            + "some kind of deadlock. We expect that either this is caused by not closing response "
                            + "bodies (there should be OkHttp log lines indicating this), or service overloading.",
                    SafeArg.of("timeout", DEFAULT_TIMEOUT));
            limiters.replace(name, limiter, newLimiter());
            return limiter(name);
        });
    }

    private static Limiter<Void> newLimiter() {
        Limiter<Void> limiter = SimpleLimiter.newBuilder()
                .limit(new RemotingWindowedLimit(VegasLimit.newDefault()))
                .build();
        return BlockingLimiter.wrap(limiter, DEFAULT_TIMEOUT);
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
