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

package com.palantir.remoting3.okhttp.metrics;

import static com.google.common.base.Preconditions.checkState;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.tags.TaggedMetric;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

public final class HostMetrics {

    public static final String SERVICE_NAME_TAG = "service-name";
    public static final String HOSTNAME_TAG = "hostname";
    public static final String FAMILY_TAG = "family";

    private final Meter informational;
    private final Meter successful;
    private final Meter redirection;
    private final Meter clientError;
    private final Meter serverError;
    private final Meter other;

    public HostMetrics(MetricRegistry registry, String serviceName, String hostname) {
        informational = registry.meter(name(serviceName, hostname, "informational"));
        successful    = registry.meter(name(serviceName, hostname, "successful"));
        redirection   = registry.meter(name(serviceName, hostname, "redirection"));
        clientError   = registry.meter(name(serviceName, hostname, "client-error"));
        serverError   = registry.meter(name(serviceName, hostname, "server-error"));
        other         = registry.meter(name(serviceName, hostname, "other"));
    }

    private static String name(String serviceName, String hostname, String family) {
        Map<String, String> tags = ImmutableMap.<String, String>builder()
                .put(SERVICE_NAME_TAG, serviceName)
                .put(HOSTNAME_TAG, hostname)
                .put(FAMILY_TAG, family)
                .build();
        return TaggedMetric.toCanonicalName("client.response", tags);
    }

    public static Optional<Meter> getMeter(
            MetricRegistry registry, String serviceName, String hostname, String family) {
        SortedMap<String, Meter> meters = registry.getMeters((name, metric) -> {
            TaggedMetric taggedMetric = TaggedMetric.from(name);
            return taggedMetric.name().equals("client.response")
                    && taggedMetric.tags().get(SERVICE_NAME_TAG).equals(serviceName)
                    && taggedMetric.tags().get(HOSTNAME_TAG).equals(hostname)
                    && taggedMetric.tags().get(FAMILY_TAG).equals(family);
        });
        checkState(meters.entrySet().size() <= 1, "Found more than one meter with given properties");
        return meters.values().stream().findFirst();
    }

    public void record(int statusCode) {
        switch (javax.ws.rs.core.Response.Status.Family.familyOf(statusCode)) {
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
    }
}
