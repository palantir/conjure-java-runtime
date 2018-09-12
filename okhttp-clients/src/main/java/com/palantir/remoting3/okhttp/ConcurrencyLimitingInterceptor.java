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

import com.google.common.collect.ImmutableSet;
import com.netflix.concurrency.limits.Limiter;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

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
final class ConcurrencyLimitingInterceptor implements Interceptor {
    private static final ImmutableSet<Integer> DROPPED_CODES = ImmutableSet.of(429, 503);

    private final ConcurrencyLimiters limiters = new ConcurrencyLimiters();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Limiter.Listener listener = limiters.limiter(chain.request());
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

    private static Response wrapResponse(Limiter.Listener listener, Response response) {
        if (response.body() == null) {
            return response;
        }
        ResponseBody currentBody = response.body();
        ResponseBody newResponseBody =
                ResponseBody.create(currentBody.contentType(), currentBody.contentLength(),
                        new ReleaseConcurrencyLimitBufferedSource(currentBody.source(), listener));
        return response.newBuilder()
                .body(newResponseBody)
                .build();
    }

    private static final class ReleaseConcurrencyLimitBufferedSource extends ForwardingBufferedSource {
        private final BufferedSource delegate;
        private final Limiter.Listener listener;

        private ReleaseConcurrencyLimitBufferedSource(BufferedSource delegate, Limiter.Listener listener) {
            super(delegate);
            this.listener = listener;
            this.delegate = delegate;
        }

        @Override
        public void close() throws IOException {
            listener.onSuccess();
            delegate.close();
        }
    }

}
