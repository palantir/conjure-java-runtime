/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.util.concurrent.RateLimiter;
import com.palantir.logsafe.SafeArg;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An interceptor that logs warnings when the response from a server contains the "deprecation" header. Logs include
 * the content of the "server" header when it is provided, and always include the provided {@code serviceClassName} so
 * that consumers applying this interceptor can locate the cause of the deprecation.
 *
 * <p>Note: endpoint information is not included because endpoint-level details are not available at this level.
 */
final class DeprecationWarningInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(DeprecationWarningInterceptor.class);
    // log at most once per minute
    private final RateLimiter loggingRateLimiter = RateLimiter.create(1.0 / 60.0);
    private final ClientMetrics clientMetrics;
    private final String serviceClassName;

    private DeprecationWarningInterceptor(ClientMetrics clientMetrics, String serviceClassName) {
        this.clientMetrics = clientMetrics;
        this.serviceClassName = serviceClassName;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());

        String deprecationHeader = response.header("deprecation");
        if (deprecationHeader != null) {
            clientMetrics.deprecations(serviceClassName).mark();

            if (loggingRateLimiter.tryAcquire(1)) {
                log.warn(
                        "Using a deprecated endpoint when connecting to service",
                        SafeArg.of("serviceClass", serviceClassName),
                        SafeArg.of("service", response.header("server", "no server header provided")));
            }
        }

        return response;
    }

    static Interceptor create(ClientMetrics clientMetrics, Class<?> serviceClass) {
        return new DeprecationWarningInterceptor(clientMetrics, serviceClass.getSimpleName());
    }
}
