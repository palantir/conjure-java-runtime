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
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limiter.BlockingLimiter;
import com.palantir.remoting3.tracing.okhttp3.OkhttpTraceInterceptor;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    private final ConcurrentMap<String, Limiter<Void>> limiters = new ConcurrentHashMap<>();

    @VisibleForTesting
    Limiter.Listener limiter(String name) {
        return limiters.computeIfAbsent(name, key ->
                new IdempotentLimiter(new BlockingLimiter<>(RemotingConcurrencyLimiter.createDefault())))
                .acquire(NO_CONTEXT).get();
    }

    Limiter.Listener limiter(Request request) {
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

    private static final class IdempotentLimiter implements Limiter<Void> {
        private final Limiter<Void> delegate;

        private IdempotentLimiter(Limiter<Void> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<Listener> acquire(Void context) {
            return delegate.acquire(context).map(IdempotentListener::new);
        }
    }

    private static final class IdempotentListener implements Limiter.Listener {
        private final Limiter.Listener delegate;
        private boolean consumed = false;

        private IdempotentListener(Limiter.Listener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSuccess() {
            if (!consumed) {
                delegate.onSuccess();
            }
            consumed = true;
        }

        @Override
        public void onIgnore() {
            if (!consumed) {
                delegate.onIgnore();
            }
            consumed = true;
        }

        @Override
        public void onDropped() {
            if (!consumed) {
                delegate.onDropped();
            }
            consumed = true;
        }
    }
}
