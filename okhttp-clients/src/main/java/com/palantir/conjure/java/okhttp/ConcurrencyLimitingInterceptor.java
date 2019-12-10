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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * Flow control in Conjure is a collaborative effort between servers and clients. Servers advertise an overloaded state
 * via 429/503 responses, and clients throttle the number of requests that they send concurrently as a response to this.
 * The latter is implemented as a combination of two techniques, yielding a mechanism similar to flow control in TCP/IP.
 *
 * <ol>
 *   <li>Clients use the frequency of 429/503 responses (as well as the request latency) to determine an estimate for
 *       the number of permissible concurrent requests
 *   <li>Each such request gets scheduled according to an exponential backoff algorithm.
 * </ol>
 *
 * <p>This class utilises Netflix's <a href="https://github.com/Netflix/concurrency-limits/">concurrency-limits</a>
 * library for determining the above mentioned concurrency estimates.
 *
 * <p>429 and 503 response codes are used for backpressure, whilst 200 -> 399 request codes are used for determining new
 * limits and all other codes are not factored in to timings.
 *
 * <p>Concurrency permits are only released when the response body is closed.
 */
final class ConcurrencyLimitingInterceptor implements Interceptor {
    private static final ImmutableSet<Integer> DROPPED_CODES = ImmutableSet.of(429, 503);

    ConcurrencyLimitingInterceptor() {}

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
            } else if (!response.isSuccessful() || response.isRedirect()) {
                listener.onIgnore();
                return response;
            } else {
                return wrapResponse(listener, response);
            }
        } catch (IOException e) {
            listener.onIgnore();
            throw e;
        }
    }

    private static Response wrapResponse(Limiter.Listener listener, Response response) throws IOException {
        // OkHttp guarantees not-null to execute() and callbacks, but not at this level.
        if (response.body() == null) {
            listener.onIgnore();
            return response;
        } else if (response.body().source().exhausted()) {
            // this case exists for Feign, which does not properly close empty responses
            listener.onSuccess();
            return response;
        }
        ResponseBody currentBody = response.body();
        ResponseBody newResponseBody = ResponseBody.create(
                currentBody.contentType(), currentBody.contentLength(), wrapSource(currentBody.source(), listener));
        return response.newBuilder().body(newResponseBody).build();
    }

    private static BufferedSource wrapSource(BufferedSource currentSource, Limiter.Listener listener) {
        return (BufferedSource) Proxy.newProxyInstance(
                BufferedSource.class.getClassLoader(),
                new Class<?>[] {BufferedSource.class},
                new ReleaseConcurrencyLimitProxy(currentSource, listener));
    }

    /** This proxy enables e.g. Okio to make additive additions to their API without breaking us. */
    private static final class ReleaseConcurrencyLimitProxy implements InvocationHandler {
        private final BufferedSource delegate;
        private final Limiter.Listener listener;
        private boolean closed = false;

        private ReleaseConcurrencyLimitProxy(BufferedSource delegate, Limiter.Listener listener) {
            this.delegate = delegate;
            this.listener = listener;
        }

        @Override
        public Object invoke(Object _proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("close") && !closed) {
                closed = true;
                listener.onSuccess();
            }

            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
