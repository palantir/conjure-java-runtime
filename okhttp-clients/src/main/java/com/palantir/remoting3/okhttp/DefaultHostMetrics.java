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

import com.codahale.metrics.Timer;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.TimeUnit;

/**
 * Records per-target-host HTTP response code metrics in a {@link TaggedMetricRegistry}.
 */
final class DefaultHostMetrics implements HostMetrics {

    private static final TimeUnit MICROS = TimeUnit.MICROSECONDS;

    // TODO(jellis): standard tags and tag names should move to public place, possibly Tritium
    static final String INFORMATIONAL = "1xx";
    static final String SUCCESSFUL = "2xx";
    static final String REDIRECTION = "3xx";
    static final String CLIENT_ERROR = "4xx";
    static final String SERVER_ERROR = "5xx";
    static final String OTHER = "other";

    private final String serviceName;
    private final String hostname;
    private final Timer informational;
    private final Timer successful;
    private final Timer redirection;
    private final Timer clientError;
    private final Timer serverError;
    private final Timer other;

    /** Creates a metrics registry for calls from the given service to the given host. */
    DefaultHostMetrics(TaggedMetricRegistry registry, String serviceName, String hostname) {
        this.serviceName = serviceName;
        this.hostname = hostname;
        this.informational = registry.timer(name(INFORMATIONAL));
        this.successful = registry.timer(name(SUCCESSFUL));
        this.redirection = registry.timer(name(REDIRECTION));
        this.clientError = registry.timer(name(CLIENT_ERROR));
        this.serverError = registry.timer(name(SERVER_ERROR));
        this.other = registry.timer(name(OTHER));
    }

    private MetricName name(String family) {
        return MetricName.builder()
                .safeName(CLIENT_RESPONSE_METRIC_NAME)
                .putSafeTags(SERVICE_NAME_TAG, serviceName)
                .putSafeTags(HOSTNAME_TAG, hostname)
                .putSafeTags(FAMILY_TAG, family)
                .build();
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
    public Timer getOther() {
        return other;
    }

    /**
     * Records that an HTTP call from the configured service to the configured host (see constructor) yielded the given
     * HTTP status code.
     */
    void record(int statusCode, long micros) {
        // Explicitly not using javax.ws.rs.core.Response API since it's incompatible across versions.
        switch (statusCode / 100) {
            case 1:
                informational.update(micros, MICROS);
                break;
            case 2:
                successful.update(micros, MICROS);
                break;
            case 3:
                redirection.update(micros, MICROS);
                break;
            case 4:
                clientError.update(micros, MICROS);
                break;
            case 5:
                serverError.update(micros, MICROS);
                break;
            default:
                other.update(micros, MICROS);
                break;
        }
    }
}
