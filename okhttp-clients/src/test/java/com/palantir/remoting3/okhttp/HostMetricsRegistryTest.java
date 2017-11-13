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

import com.google.common.collect.Iterables;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import org.junit.Before;
import org.junit.Test;

public final class HostMetricsRegistryTest {

    private TaggedMetricRegistry registry;
    private HostMetricsRegistry hostRegistry;

    @Before
    public void before() {
        registry = new DefaultTaggedMetricRegistry();
        hostRegistry = new HostMetricsRegistry(registry);
    }

    @Test
    public void testMetricsUpdated() {
        assertThat(hostRegistry.getMetrics()).isEmpty();

        hostRegistry.record("service", "host", 200);

        HostMetrics hostMetrics = Iterables.getOnlyElement(hostRegistry.getMetrics());
        assertThat(hostMetrics.get2xx().getCount()).isEqualTo(1);
    }

    @Test
    public void testMetricsRegistered() {
        MetricName name = MetricName.builder()
                .safeName(HostMetrics.CLIENT_RESPONSE_METRIC_NAME)
                .putSafeTags(HostMetrics.SERVICE_NAME_TAG, "service")
                .putSafeTags(HostMetrics.HOSTNAME_TAG, "host")
                .putSafeTags(HostMetrics.FAMILY_TAG, HostMetrics.SUCCESSFUL)
                .build();

        assertThat(registry.getMetrics().get(name)).isNull();
        hostRegistry.record("service", "host", 200);
        assertThat(registry.meter(name).getCount()).isEqualTo(1);
    }
}
