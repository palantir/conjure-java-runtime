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
import org.junit.Before;
import org.junit.Test;

public final class HostMetricsRegistryTest {

    private HostMetricsRegistry hostRegistry;

    @Before
    public void before() {
        hostRegistry = new HostMetricsRegistry();
    }

    @Test
    public void testMetricsUpdated() {
        assertThat(hostRegistry.getMetrics()).isEmpty();

        hostRegistry.record("service", "host", "http://host", 200, 1);

        HostMetrics hostMetrics = Iterables.getOnlyElement(hostRegistry.getMetrics());
        assertThat(hostMetrics.get2xx().getSnapshot().getMin()).isEqualTo(1_000);
    }

    @Test
    public void testIoExceptionsUpdated() {
        assertThat(hostRegistry.getMetrics()).isEmpty();

        hostRegistry.recordIoException("service", "host", "http://host");

        HostMetrics hostMetrics = Iterables.getOnlyElement(hostRegistry.getMetrics());
        assertThat(hostMetrics.getIoExceptions().getCount()).isEqualTo(1);
    }
}
