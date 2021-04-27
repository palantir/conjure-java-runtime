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
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** Records per-target-host HTTP response code metrics in a {@link TaggedMetricRegistry}. */
final class DefaultHostMetrics implements HostMetrics {

    private static final TimeUnit MICROS = TimeUnit.MICROSECONDS;

    private final String serviceName;
    private final String hostname;
    private final int port;
    private final Timer informational;
    private final Timer successful;
    private final Timer redirection;
    private final Timer clientError;
    private final Timer serverError;
    private final Timer qos;
    private final Timer other;
    private final Meter ioExceptions;
    private final Clock clock;

    private volatile long lastUpdateEpochMillis;

    /** Creates a metrics registry for calls from the given service to the given host and port. */
    DefaultHostMetrics(String serviceName, String hostname, int port, Clock clock) {
        this.serviceName = serviceName;
        this.hostname = hostname;
        this.port = port;
        this.informational = new Timer();
        this.successful = new Timer();
        this.redirection = new Timer();
        this.clientError = new Timer();
        this.serverError = new Timer();
        this.qos = new Timer();
        this.other = new Timer();
        this.ioExceptions = new Meter();
        this.clock = clock;
        this.lastUpdateEpochMillis = clock.millis();
    }

    @Override
    public String serviceName() {
        return serviceName;
    }

    @Override
    public String hostname() {
        return hostname;
    }

    @Override
    public int port() {
        return port;
    }

    @Override
    public Instant lastUpdate() {
        return Instant.ofEpochMilli(lastUpdateEpochMillis);
    }

    @Override
    public Timer get1xx() {
        return informational;
    }

    @Override
    public Timer get2xx() {
        return successful;
    }

    @Override
    public Timer get3xx() {
        return redirection;
    }

    @Override
    public Timer get4xx() {
        return clientError;
    }

    @Override
    public Timer get5xx() {
        return serverError;
    }

    @Override
    public Timer getQos() {
        return qos;
    }

    @Override
    public Timer getOther() {
        return other;
    }

    @Override
    public Meter getIoExceptions() {
        return ioExceptions;
    }

    /**
     * Records that an HTTP call from the configured service to the configured host (see constructor) yielded the given
     * HTTP status code.
     */
    void record(int statusCode, long micros) {
        timer(statusCode).update(micros, MICROS);
        lastUpdateEpochMillis = clock.millis();
    }

    void recordIoException() {
        ioExceptions.mark();
        lastUpdateEpochMillis = clock.millis();
    }

    private Timer timer(int statusCode) {
        // Explicitly not using javax.ws.rs.core.Response API since it's incompatible across versions.
        if (statusCode == 429 || statusCode == 503) {
            return qos;
        }

        switch (statusCode / 100) {
            case 1:
                return informational;
            case 2:
                return successful;
            case 3:
                return redirection;
            case 4:
                return clientError;
            case 5:
                return serverError;
        }

        return other;
    }
}
