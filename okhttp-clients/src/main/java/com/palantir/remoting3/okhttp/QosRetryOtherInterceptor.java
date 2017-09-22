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

import com.google.common.net.HttpHeaders;
import com.palantir.logsafe.SafeArg;
import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Intercepts HTTP 308 responses and retries the given request against the URL indicated in the response's {@code
 * Location} header. Redirects are only performed against URLs accepted by the configured {@link UrlSelector}. See
 * {@link okhttp3.internal.http.RetryAndFollowUpInterceptor} for inspiration.
 */
class QosRetryOtherInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(QosRetryOtherInterceptor.class);
    private static final int MAX_NUM_REDIRECTS = 20;

    private final UrlSelector urls;

    QosRetryOtherInterceptor(UrlSelector urls) {
        this.urls = urls;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request currentRequest = chain.request();
        Response priorResponse = null;
        int numRedirects = 0;
        while (numRedirects <= MAX_NUM_REDIRECTS) {
            Response response = chain.proceed(currentRequest);
            // See RetryAndFollowUpInterceptor
            if (priorResponse != null) {
                response = response.newBuilder()
                        .priorResponse(priorResponse.newBuilder()
                                .body(null)
                                .build())
                        .build();
            }

            if (response.code() != 308) {
                return response;
            } else {
                String locationHeader = response.header(HttpHeaders.LOCATION);
                if (locationHeader == null) {
                    throw new IOException("Retrieved HTTP status code 308 without Location header, cannot perform "
                            + "redirect. This appears to be a server-side protocol violation.");
                }

                HttpUrl redirectTo = urls.redirectTo(currentRequest.url(), locationHeader)
                        .orElseThrow(() -> new IOException("Failed to determine valid redirect URL for Location "
                                + "header '" + locationHeader + "' and base URLs " + urls.getBaseUrls()));

                // Note: Do not SafeArg-log the redirectTo URL since it typically contains unsafe information
                log.debug("Received 308 response, retrying host at advertised location",
                        SafeArg.of("location", locationHeader));
                currentRequest = currentRequest.newBuilder().url(redirectTo).build();
                priorResponse = response;
                numRedirects += 1;
            }
        }

        throw new IOException(
                "Exceeded the maximum number of allowed redirects for initial URL: " + chain.request().url());
    }
}
