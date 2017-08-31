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
import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * An OkHttp {@link Interceptor} that turns HTTP responses pertaining to http-remoting {@link QosException}s into {@link
 * QosIoException}s.
 * <p>
 * See {@link QosIoExceptionHandler} for an end-to-end explanation of http-remoting specific client-side error
 * handling.
 */
final class QosIoExceptionInterceptor implements Interceptor {
    static final QosIoExceptionInterceptor INSTANCE = new QosIoExceptionInterceptor();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        switch (response.code()) {
            case 308:
                throw handle308(response);
            case 429:
                throw handle429(response);
            case 503:
                throw handle503(response);
        }

        return response;
    }

    private static IOException handle308(Response response) {
        String locationHeader = response.header(HttpHeaders.LOCATION);
        if (locationHeader == null) {
            return new IOException("Retrieved HTTP status code 308 without Location header, cannot perform "
                    + "redirect. This appears to be a server-side protocol violation.");
        }

        try {
            return new QosIoException(QosException.retryOther(new URL(locationHeader)), response);
        } catch (MalformedURLException e) {
            return new IOException("Failed to parse redirect URL from 'Location' response header: " + locationHeader);
        }
    }

    private static IOException handle429(Response response) {
        String duration = response.header(HttpHeaders.RETRY_AFTER);
        if (duration == null) {
            return new QosIoException(QosException.throttle(), response);
        } else {
            return new QosIoException(QosException.throttle(Duration.ofSeconds(Long.parseLong(duration))), response);
        }
    }

    private static IOException handle503(Response response) {
        return new QosIoException(QosException.unavailable(), response);
    }
}
