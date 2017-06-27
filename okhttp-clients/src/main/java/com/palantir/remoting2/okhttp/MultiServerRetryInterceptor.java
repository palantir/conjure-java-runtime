/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.okhttp;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.HttpRetryException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MultiServerRetryInterceptor implements Interceptor {
    private static final Logger logger = LoggerFactory.getLogger(MultiServerRetryInterceptor.class);

    private final List<HttpUrl> urls;

    private static final Comparator<HttpUrl> FQDN_COMPARATOR =
            Comparator.comparing(HttpUrl::scheme)
                    .thenComparing(HttpUrl::host)
                    .thenComparing(HttpUrl::port);

    private MultiServerRetryInterceptor(List<String> uris) {
        this.urls = ImmutableList.copyOf(uris.stream().map(HttpUrl::parse).collect(Collectors.toList()));
    }

    public static MultiServerRetryInterceptor create(List<String> uris) {
        return create(uris, true);
    }

    public static MultiServerRetryInterceptor create(List<String> uris, boolean randomize) {
        Preconditions.checkArgument(!uris.isEmpty());

        if (randomize) {
            List<String> shuffledUris = new ArrayList<>(uris);
            Collections.shuffle(shuffledUris);
            return new MultiServerRetryInterceptor(shuffledUris);
        }

        return new MultiServerRetryInterceptor(uris);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        Exception lastException = null;
        HttpUrl lastUrl = null;

        for (HttpUrl url : urls) {
            if (lastUrl != null) {
                logger.warn("Redirecting request from {} to {}", lastUrl, url);
            }
            request = redirectRequest(request, url);
            try {
                return chain.proceed(request);
            } catch (SocketTimeoutException | UnknownHostException | HttpRetryException
                    | MalformedURLException | SocketException | UnknownServiceException e) {
                lastException = e;
                lastUrl = url;
                logger.warn("Failed to send request to {}", request.url(), e);
            }
        }
        throw new IllegalStateException("Could not connect to any of the following servers: " + urls + ". "
                + "Please check that the URIs are correct and servers are accessible.", lastException);
    }

    private Request redirectRequest(Request request, HttpUrl redirectToUrl) {
        HttpUrl requestUrl = request.url();

        HttpUrl matchingUrl = null;
        // Find which server from `uris` is used in the current request, then ...
        for (HttpUrl url : urls) {
            if (doServersMatch(requestUrl, url)) {
                matchingUrl = url;
                break;
            }
        }

        if (matchingUrl == null) {
            throw new IllegalStateException(String.format("Unrecognized server URI in the request %s. "
                            + "Supported servers are %s. Did you use different server URI for the initial request?",
                    requestUrl, urls));
        }

        // ... replace it with the URI of the server to redirect to.
        String newRequestUrl = requestUrl.toString().replaceFirst(matchingUrl.toString(), redirectToUrl.toString());
        return request.newBuilder()
                .url(HttpUrl.parse(newRequestUrl))
                // Request.this.tag field by default points to request itself if it was not set in RequestBuilder.
                // We don't want to reference old request in new one - that is why we need to reset tag to null.
                .tag(request.tag().equals(request) ? null : request.tag())
                .build();
    }

    private boolean doServersMatch(HttpUrl fullUrl, HttpUrl prefixUrl) {
        return FQDN_COMPARATOR.compare(fullUrl, prefixUrl) == 0
                        && fullUrl.encodedPath().startsWith(prefixUrl.encodedPath());
    }
}
