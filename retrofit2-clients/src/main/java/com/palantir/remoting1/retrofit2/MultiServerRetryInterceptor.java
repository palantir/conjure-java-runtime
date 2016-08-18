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

package com.palantir.remoting1.retrofit2;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.net.HttpRetryException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MultiServerRetryInterceptor implements Interceptor {
    private static final Logger logger = LoggerFactory.getLogger(MultiServerRetryInterceptor.class);

    private final List<String> uris;

    private MultiServerRetryInterceptor(List<String> uris) {
        this.uris = uris;
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

        for (String uri : uris) {
            request = redirectRequest(request, uri);
            try {
                return chain.proceed(request);
            } catch (SocketTimeoutException | UnknownHostException | HttpRetryException
                    | MalformedURLException | SocketException | UnknownServiceException e) {
                lastException = e;
                logger.warn("Failed to send request to " + request.url(), e);
            } catch (IOException e) {
                throw e;
            }
        }
        throw new IllegalStateException(
                "Could not connect to any node. Please check that the URIs are valid and servers are up.",
                lastException);
    }

    private Request redirectRequest(Request request, String redirectToUri) {
        final String currentUrl = request.url().toString();

        String matchingUri = null;
        for (String uri : uris) {
            if (currentUrl.startsWith(uri)) {
                matchingUri = uri;
                break;
            }
        }

        if (matchingUri == null) {
            throw new IllegalStateException(
                    String.format("None of the URIs %s matched request URL %s", uris, currentUrl));
        }

        final String newUrl = currentUrl.replaceFirst(matchingUri, redirectToUri);

        return request.newBuilder()
                .url(HttpUrl.parse(newUrl))
                // Request.this.tag field by default points to request itself if it was not set in RequestBuilder.
                // We don't want to reference old request in new one - that is why we need to reset tag to null.
                .tag(request.tag().equals(request) ? null : request.tag())
                .build();
    }
}
