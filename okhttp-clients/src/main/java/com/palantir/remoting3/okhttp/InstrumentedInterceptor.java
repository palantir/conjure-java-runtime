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

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Records metrics about the response codes of http requests.
 */
public final class InstrumentedInterceptor implements Interceptor {

    private final Meter informational;
    private final Meter successful;
    private final Meter redirection;
    private final Meter clientError;
    private final Meter serverError;
    private final Meter other;

    public InstrumentedInterceptor(MetricRegistry registry, String name) {
        informational = registry.meter(MetricRegistry.name(name, "response", "code", "1xx"));
        successful    = registry.meter(MetricRegistry.name(name, "response", "code", "2xx"));
        redirection   = registry.meter(MetricRegistry.name(name, "response", "code", "3xx"));
        clientError   = registry.meter(MetricRegistry.name(name, "response", "code", "4xx"));
        serverError   = registry.meter(MetricRegistry.name(name, "response", "code", "5xx"));
        other         = registry.meter(MetricRegistry.name(name, "response", "code", "other"));
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());

        switch (javax.ws.rs.core.Response.Status.Family.familyOf(response.code())) {
            case INFORMATIONAL:
                informational.mark();
                break;
            case SUCCESSFUL:
                successful.mark();
                break;
            case REDIRECTION:
                redirection.mark();
                break;
            case CLIENT_ERROR:
                clientError.mark();
                break;
            case SERVER_ERROR:
                serverError.mark();
                break;
            case OTHER:
                other.mark();
                break;
        }

        return response;
    }
}
