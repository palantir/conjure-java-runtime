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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HttpHeaders;
import com.palantir.logsafe.SafeArg;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logs a warning whe an HTTP response contains an API deprecation warning header. */
final class DeprecationWarningLoggingInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(DeprecationWarningLoggingInterceptor.class);

    private final Class<?> serviceClass;

    // Keep in sync with DeprecationWarningFilter.
    private static final Pattern VALID_DEPRECATION_WARNING =
            Pattern.compile(Pattern.quote("299 - \"Service API endpoint is deprecated: ") + "(.*)\"");

    DeprecationWarningLoggingInterceptor(Class<?> serviceClass) {
        this.serviceClass = serviceClass;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        extractDeprecatedPath(response.headers(HttpHeaders.WARNING)).ifPresent(path ->
                log.warn("API endpoint is reported as being deprecated in the remote service",
                        SafeArg.of("path", path),
                        SafeArg.of("serviceClass", serviceClass.getCanonicalName())));
        return response;
    }

    @VisibleForTesting
    static Optional<String> extractDeprecatedPath(List<String> warningHeaders) {
        for (String warningHeader : warningHeaders) {
            Matcher matcher = VALID_DEPRECATION_WARNING.matcher(warningHeader);
            if (matcher.matches()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }
}
