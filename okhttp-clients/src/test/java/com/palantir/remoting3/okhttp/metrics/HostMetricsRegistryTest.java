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

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;

public final class HostMetricsRegistryTest {

    @Test
    public void test() {
        MetricRegistry registry = new MetricRegistry();
        HostMetricsRegistry hostRegistry = new HostMetricsRegistry(registry, "service");

        assertThat(HostMetricsTest.getMeter(registry, "service", "host", "successful")).isEmpty();
        hostRegistry.record("host", 200);
        assertThat(HostMetricsTest.getMeter(registry, "service", "host", "successful").get().getCount()).isEqualTo(1);
    }
}
