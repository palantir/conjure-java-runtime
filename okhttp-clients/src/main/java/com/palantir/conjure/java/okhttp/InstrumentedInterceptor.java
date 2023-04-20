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

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.google.common.base.Stopwatch;
import com.palantir.conjure.java.client.config.HostEventsSink;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Response;

/** Records metrics about the response codes of http requests. */
final class InstrumentedInterceptor implements Interceptor {

    private final HostEventsSink hostEventsSink;
    private final String serviceName;
    private final Timer responseTimer;
    private final Meter ioExceptionMeter;

    InstrumentedInterceptor(ClientMetrics clientMetrics, HostEventsSink hostEventsSink, String serviceName) {
        this.hostEventsSink = hostEventsSink;
        this.serviceName = serviceName;
        this.responseTimer = clientMetrics.response(serviceName);
        this.ioExceptionMeter = clientMetrics
                .responseError()
                .reason("IOException")
                .serviceName(serviceName)
                .build();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        HttpUrl url = chain.request().url();
        String hostname = url.host();
        int port = url.port();
        Stopwatch stopwatch = Stopwatch.createStarted();
        Response response;

        try {
            response = chain.proceed(chain.request());
        } catch (IOException e) {
            if (!chain.call().isCanceled()) {
                hostEventsSink.recordIoException(serviceName, hostname, port);
                ioExceptionMeter.mark();
            }
            throw e;
        }

        long micros = stopwatch.elapsed(TimeUnit.MICROSECONDS);

        hostEventsSink.record(serviceName, hostname, port, response.code(), micros);
        responseTimer.update(Duration.of(micros, ChronoUnit.MICROS));

        return response;
    }

    static InstrumentedInterceptor create(
            ClientMetrics clientMetrics, HostEventsSink hostEventsSink, Class<?> serviceClass) {
        return new InstrumentedInterceptor(clientMetrics, hostEventsSink, serviceClass.getSimpleName());
    }
}
