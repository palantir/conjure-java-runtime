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

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.Timer;
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
    private DefaultHostMetrics hostMetrics;

    @Before
    public void before() {
        registry = new DefaultTaggedMetricRegistry();
        hostMetrics = new DefaultHostMetrics(registry, SERVICE_NAME, HOSTNAME);
    }

    @Test
    public void testUpdateMetricUpdatesMeter() {
        Map<Integer, String> testCases = ImmutableMap.<Integer, String>builder()
                .put(100, DefaultHostMetrics.INFORMATIONAL)
                .put(200, DefaultHostMetrics.SUCCESSFUL)
                .put(300, DefaultHostMetrics.REDIRECTION)
                .put(400, DefaultHostMetrics.CLIENT_ERROR)
                .put(500, DefaultHostMetrics.SERVER_ERROR)
                .put(600, DefaultHostMetrics.OTHER)
                .build();

        for (Map.Entry<Integer, String> testCase : testCases.entrySet()) {
            Timer timer = getMeter(registry, SERVICE_NAME, HOSTNAME, testCase.getValue()).get();
            assertThat(timer.getCount()).isZero();
            assertThat(timer.getSnapshot().getMin()).isEqualTo(0);

            hostMetrics.record(testCase.getKey(), 1);

            assertThat(timer.getCount()).isEqualTo(1);
            assertThat(timer.getSnapshot().getMin()).isEqualTo(1_000);
        }
    }

    public static Optional<Timer> getMeter(
            TaggedMetricRegistry registry, String serviceName, String hostname, String family) {
        MetricName name = MetricName.builder()
                .safeName(HostMetrics.CLIENT_RESPONSE_METRIC_NAME)
                .putSafeTags(HostMetrics.SERVICE_NAME_TAG, serviceName)
                .putSafeTags(HostMetrics.HOSTNAME_TAG, hostname)
                .putSafeTags(HostMetrics.FAMILY_TAG, family)
                .build();
        return Optional.ofNullable((Timer) registry.getMetrics().get(name));
    }

}
