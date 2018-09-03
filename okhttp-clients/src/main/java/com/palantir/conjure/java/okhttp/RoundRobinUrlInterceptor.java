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

import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * An {@link Interceptor} implementation that rebases the request's URL in a round robin fashion. The rebased URL will
 * be the next URL after the last recorded URL used when multiple URLs are supplied via the {@link UrlSelector}.
 */
final class RoundRobinUrlInterceptor implements Interceptor {

    private final UrlSelector urls;

    private RoundRobinUrlInterceptor(UrlSelector urls) {
        this.urls = urls;
    }

    static RoundRobinUrlInterceptor create(UrlSelector urls) {
        return new RoundRobinUrlInterceptor(urls);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        HttpUrl rebasedUrl = urls.redirectToNextRoundRobin(originalRequest.url()).orElseThrow(() -> new IOException(
                "Failed to determine suitable target URL for request URL " + originalRequest.url()
                        + " amongst known base URLs: " + urls.getBaseUrls()));
        Request request = originalRequest.newBuilder()
                .url(rebasedUrl)
                .build();
        return chain.proceed(request);
    }
}
