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
import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import java.time.Duration;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An OkHttp {@link Interceptor} that turns HTTP responses pertaining to http-remoting {@link QosException}s into {@link
 * QosIoException}s. Only exceptions that require delaying/throttling/postponing a request are handled by this
 * interceptor.
 * <p>
 * See {@link CallRetryer} for an end-to-end explanation of http-remoting specific client-side error
 * handling.
 */
final class QosRetryLaterInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(QosRetryLaterInterceptor.class);

    static final QosRetryLaterInterceptor INSTANCE = new QosRetryLaterInterceptor();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        switch (response.code()) {
            case 429:
                throw handle429(response);
            case 503:
                throw handle503(response);
        }

        return response;
    }

    private static IOException handle429(Response response) {
        String duration = response.header(HttpHeaders.RETRY_AFTER);
        if (duration == null) {
            log.debug("Received 429 response, throwing QosException to trigger delayed retry");
            return new QosIoException(QosException.throttle(), response);
        } else {
            log.debug("Received 429 response, throwing QosException to trigger delayed retry",
                    SafeArg.of("duration", duration));
            return new QosIoException(QosException.throttle(Duration.ofSeconds(Long.parseLong(duration))), response);
        }
    }

    private static IOException handle503(Response response) {
        log.debug("Received 503 response, throwing QosException to trigger delayed retry");
        return new QosIoException(QosException.unavailable(), response);
    }
}
