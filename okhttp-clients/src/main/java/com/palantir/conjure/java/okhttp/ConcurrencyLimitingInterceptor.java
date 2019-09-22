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

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.netflix.concurrency.limits.Limiter;
import com.palantir.logsafe.Preconditions;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

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
 * This class utilises Netflix's
 * <a href="https://github.com/Netflix/concurrency-limits/">concurrency-limits</a> library for determining the
 * above mentioned concurrency estimates.
 * <p>
 * 429 and 503 response codes are used for backpressure, whilst 200 -> 399 request codes are used for determining
 * new limits and all other codes are not factored in to timings.
 * <p>
 * Concurrency permits are released when the response is received.
 */
final class ConcurrencyLimitingInterceptor implements Interceptor {
    private static final ImmutableSet<Integer> DROPPED_CODES = ImmutableSet.of(429, 503);

    ConcurrencyLimitingInterceptor() { }

    @Override
    public Response intercept(Chain chain) throws IOException {
        ConcurrencyLimiterListener limiterListenerTag = chain.request().tag(ConcurrencyLimiterListener.class);
        if (limiterListenerTag == null) {
            return chain.proceed(chain.request());
        }

        ListenableFuture<Limiter.Listener> limiterFuture = limiterListenerTag.limiterListener();
        Preconditions.checkState(limiterFuture.isDone(), "Limit listener future should have been fulfilled.");
        Limiter.Listener listener = Futures.getUnchecked(limiterFuture);

        try {
            Response response = chain.proceed(chain.request());
            if (DROPPED_CODES.contains(response.code())) {
                listener.onDropped();
                return response;
            } else if (!response.isSuccessful() || response.isRedirect() || response.body() == null) {
                listener.onIgnore();
                return response;
            } else {
                listener.onSuccess();
                return response;
            }
        } catch (IOException e) {
            listener.onIgnore();
            throw e;
        }
    }
}
