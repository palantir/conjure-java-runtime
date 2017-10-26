/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

import java.io.IOException;
import java.io.InterruptedIOException;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MultiServerRetryInterceptor implements Interceptor {
    private static final Logger logger = LoggerFactory.getLogger(MultiServerRetryInterceptor.class);

    private final UrlSelector urls;
    private final int maxNumRetries;

    private MultiServerRetryInterceptor(UrlSelector urls, int maxNumRetries) {
        this.urls = urls;
        this.maxNumRetries = maxNumRetries;
    }

    public static MultiServerRetryInterceptor create(UrlSelector urls, int maxNumRetries) {
        return new MultiServerRetryInterceptor(urls, maxNumRetries);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Exception lastException;

        // Try original request first.
        try {
            return chain.proceed(chain.request());
        } catch (InterruptedIOException e) {
            throw e;
        } catch (IOException e) {
            lastException = e;
            logger.warn("Failed to send request to {}", chain.request().url(), e);
        }

        // If the original URL failed, retry according to the UrlSelector.
        HttpUrl currentUrl = chain.request().url();
        for (int i = 0; i < maxNumRetries; ++i) {
            HttpUrl nextUrl = urls.redirectToNext(currentUrl).orElseThrow(() -> new IOException(
                    "Failed to determine suitable target URL for request URL " + chain.request().url()
                            + " amongst known base URLs: " + urls.getBaseUrls()));
            logger.debug("Redirecting request from {} to {}", currentUrl, nextUrl);
            Request originalRequest = chain.request();
            Request request = originalRequest.newBuilder()
                    .url(nextUrl)
                    // Request.this.tag field by default points to request itself if it was not set in RequestBuilder.
                    // We don't want to reference old request in new one - that is why we need to reset tag to null.
                    .tag(originalRequest.tag().equals(originalRequest) ? null : originalRequest.tag())
                    .build();
            try {
                return chain.proceed(request);
            } catch (InterruptedIOException e) {
                throw e;
            } catch (IOException e) {
                lastException = e;
                currentUrl = nextUrl;
                logger.warn("Failed to send request to {}", request.url(), e);
            }
        }
        throw new IOException("Could not connect to any of the configured URLs: " + urls.getBaseUrls() + ". "
                + "Please check that the URIs are correct and servers are accessible.", lastException);
    }
}
