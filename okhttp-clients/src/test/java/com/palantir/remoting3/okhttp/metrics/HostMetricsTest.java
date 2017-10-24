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
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public final class HostMetricsTest {

    private MetricRegistry registry;
    private HostMetrics hostMetrics;

    @Before
    public void before() {
        registry = new MetricRegistry();
        hostMetrics = new HostMetrics(registry, "serviceName", "hostName");
    }

    @Test
    public void testUpdateMetricUpdatesMeter() {
        Map<Integer, String> testCases = ImmutableMap.<Integer, String>builder()
                .put(100, "informational")
                .put(200, "successful")
                .put(300, "redirection")
                .put(400, "client-error")
                .put(500, "server-error")
                .put(600, "other")
                .build();

        for (Map.Entry<Integer, String> testCase : testCases.entrySet()) {
            Meter meter = registry.getMeters().get("serviceName.response.family." + testCase.getValue());
            assertThat(meter.getCount()).isZero();

            hostMetrics.record(testCase.getKey());

            assertThat(meter.getCount()).isEqualTo(1);
        }
    }
}
