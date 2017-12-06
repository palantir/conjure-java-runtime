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

package com.palantir.remoting3.okhttp;

import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Records metrics about the response codes of http requests.
 */
final class InstrumentedInterceptor implements Interceptor {

    private final HostMetricsRegistry hostMetrics;
    private final String serviceName;

    InstrumentedInterceptor(HostMetricsRegistry hostMetrics, String serviceName) {
        this.hostMetrics = hostMetrics;
        this.serviceName = serviceName;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Response response = chain.proceed(chain.request());
        long micros = stopwatch.elapsed(TimeUnit.MICROSECONDS);

        String hostname = chain.request().url().host();
        hostMetrics.record(serviceName, hostname, response.code(), micros);

        return response;
    }

    static InstrumentedInterceptor create(HostMetricsRegistry hostMetrics, Class<?> serviceClass) {
        return new InstrumentedInterceptor(hostMetrics, serviceClass.getSimpleName());
    }
}
