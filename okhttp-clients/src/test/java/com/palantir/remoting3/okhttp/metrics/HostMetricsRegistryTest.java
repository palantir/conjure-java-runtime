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

package com.palantir.remoting3.okhttp.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import org.junit.Test;

public final class HostMetricsRegistryTest {

    @Test
    public void test() {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        HostMetricsRegistry hostRegistry = new HostMetricsRegistry(registry, "service");

        MetricName name = MetricName.builder()
                .safeName(HostMetrics.CLIENT_RESPONSE_METRIC_NAME)
                .putSafeTags(HostMetrics.SERVICE_NAME_TAG, "service")
                .putSafeTags(HostMetrics.HOSTNAME_TAG, "host")
                .putSafeTags(HostMetrics.FAMILY_TAG, HostMetrics.SUCCESSFUL)
                .build();

        assertThat(registry.getMetrics().get(name)).isNull();
        hostRegistry.record("host", 200);
        assertThat(registry.meter(name).getCount()).isEqualTo(1);
    }
}
