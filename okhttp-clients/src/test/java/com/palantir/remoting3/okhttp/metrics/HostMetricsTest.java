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

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.Meter;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public final class HostMetricsTest {

    private static final String SERVICE_NAME = "serviceName";
    private static final String HOSTNAME = "hostname";

    private TaggedMetricRegistry registry;
    private HostMetrics hostMetrics;

    @Before
    public void before() {
        registry = new DefaultTaggedMetricRegistry();
        hostMetrics = new HostMetrics(registry, SERVICE_NAME, HOSTNAME);
    }

    @Test
    public void testUpdateMetricUpdatesMeter() {
        Map<Integer, String> testCases = ImmutableMap.<Integer, String>builder()
                .put(100, HostMetrics.INFORMATIONAL)
                .put(200, HostMetrics.SUCCESSFUL)
                .put(300, HostMetrics.REDIRECTION)
                .put(400, HostMetrics.CLIENT_ERROR)
                .put(500, HostMetrics.SERVER_ERROR)
                .put(600, HostMetrics.OTHER)
                .build();

        for (Map.Entry<Integer, String> testCase : testCases.entrySet()) {
            Meter meter = getMeter(registry, SERVICE_NAME, HOSTNAME, testCase.getValue()).get();
            assertThat(meter.getCount()).isZero();

            hostMetrics.record(testCase.getKey());

            assertThat(meter.getCount()).isEqualTo(1);
        }
    }

    public static Optional<Meter> getMeter(
            TaggedMetricRegistry registry, String serviceName, String hostname, String family) {
        MetricName name = MetricName.builder()
                .safeName(HostMetrics.CLIENT_RESPONSE_METRIC_NAME)
                .putSafeTags(HostMetrics.SERVICE_NAME_TAG, serviceName)
                .putSafeTags(HostMetrics.HOSTNAME_TAG, hostname)
                .putSafeTags(HostMetrics.FAMILY_TAG, family)
                .build();
        return Optional.ofNullable((Meter) registry.getMetrics().get(name));
    }

}
