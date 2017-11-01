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
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MultiServerRequestCreator {
    private static final Logger logger = LoggerFactory.getLogger(MultiServerRequestCreator.class);

    private final UrlSelector urls;

    MultiServerRequestCreator(UrlSelector urls) {
        this.urls = urls;
    }

    Request getNextRequest(Request originalRequest) throws IOException {
        HttpUrl originalUrl = originalRequest.url();
        HttpUrl nextUrl = urls.redirectToNext(originalUrl).orElseThrow(() -> new IOException(
                "Failed to determine suitable target URL for request URL " + originalUrl
                        + " amongst known base URLs: " + urls.getBaseUrls()));
        logger.debug("Creating a new request that redirects from {} to {}", originalUrl, nextUrl);
        return originalRequest.newBuilder()
                .url(nextUrl)
                // Request.this.tag field by default points to request itself if it was not set in RequestBuilder.
                // We don't want to reference old request in new one - that is why we need to reset tag to null.
                .tag(originalRequest.tag().equals(originalRequest) ? null : originalRequest.tag())
                .build();
    }
}
