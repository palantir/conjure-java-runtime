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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.palantir.remoting3.okhttp.metrics.HostMetricsRegistry;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records metrics about the response codes of http requests.
 */
public final class InstrumentedInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(InstrumentedInterceptor.class);

    private final HostMetricsRegistry hostMetrics;

    InstrumentedInterceptor(MetricRegistry registry, String serviceName) {
        this.hostMetrics = new HostMetricsRegistry(registry, serviceName);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        String hostname = chain.request().url().host();

        hostMetrics.record(hostname, response.code());

        return response;
    }

    static InstrumentedInterceptor withDefaultMetricRegistry(String serviceName) {
        MetricRegistry registry = SharedMetricRegistries.tryGetDefault();
        if (registry != null) {
            return new InstrumentedInterceptor(registry, serviceName);
        } else {
            log.info("Response metrics will not be available because no MetricRegistry was found");
            return new InstrumentedInterceptor(new MetricRegistry(), serviceName);
        }
    }
}
