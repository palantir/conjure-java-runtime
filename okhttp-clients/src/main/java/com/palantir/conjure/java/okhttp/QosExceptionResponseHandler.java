/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ResponseHandler} that turns QOS-related HTTP responses into {@link QosException}s.
 */
enum QosExceptionResponseHandler implements ResponseHandler<QosException> {
    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(QosExceptionResponseHandler.class);

    @Override
    public Optional<QosException> handle(Response response) {
        switch (response.code()) {
            case 308:
                return handle308(response);
            case 429:
                return Optional.of(handle429(response));
            case 503:
                return Optional.of(handle503());
        }

        return Optional.empty();
    }

    private Optional<QosException> handle308(Response response) {
        String locationHeader = response.header(HttpHeaders.LOCATION);
        if (locationHeader == null) {
            log.error("Retrieved HTTP status code 308 without Location header, cannot perform "
                    + "redirect. This appears to be a server-side protocol violation.");
            return Optional.empty();
        }
        // Note: Do not SafeArg-log the redirectTo URL since it typically contains unsafe information
        log.debug("Received 308 response, retrying host at advertised location",
                SafeArg.of("location", locationHeader));

        try {
            return Optional.of(QosException.retryOther(new URL(locationHeader)));
        } catch (MalformedURLException e) {
            log.error("Failed to parse location header, not performing redirect",
                    UnsafeArg.of("locationHeader", locationHeader), e);
            return Optional.empty();
        }
    }

    private static QosException handle429(Response response) {
        String duration = response.header(HttpHeaders.RETRY_AFTER);
        if (duration == null) {
            log.debug("Received 429 response, throwing QosException to trigger delayed retry");
            return QosException.throttle();
        } else {
            log.debug("Received 429 response, throwing QosException to trigger delayed retry",
                    SafeArg.of("duration", duration));
            return QosException.throttle(Duration.ofSeconds(Long.parseLong(duration)));
        }
    }

    private static QosException handle503() {
        log.debug("Received 503 response, throwing QosException to trigger failover");
        return QosException.unavailable();
    }
}
