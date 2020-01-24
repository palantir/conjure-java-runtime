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

import com.palantir.logsafe.SafeArg;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DeprecationWarningInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(DeprecationWarningInterceptor.class);
    private final String serviceClassName;

    private DeprecationWarningInterceptor(String serviceClassName) {
        this.serviceClassName = serviceClassName;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());

        if (response.isSuccessful()) {
            String deprecationHeader = response.header("deprecation");
            if (deprecationHeader != null) {
                log.warn("Using a deprecated endpoint when connecting to service",
                        SafeArg.of("serviceClass", serviceClassName),
                        SafeArg.of("service", response.header("server", "no server header provided")));
            }
        }

        return response;
    }

    static Interceptor create(Class<?> serviceClass) {
        return new DeprecationWarningInterceptor(serviceClass.getSimpleName());
    }
}
