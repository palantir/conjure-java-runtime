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

package com.palantir.remoting3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.Meter;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.collect.ImmutableList;
import com.palantir.remoting.api.config.ssl.SslConfiguration;
import com.palantir.remoting3.clients.ClientConfiguration;
import com.palantir.remoting3.clients.ClientConfigurations;
import com.palantir.remoting3.config.ssl.SslSocketFactories;
import java.nio.file.Paths;
import java.util.SortedMap;
import org.junit.Test;

public final class OkHttpClientsTest {

    @Test
    public void testCreate_ensureMetricsAreRegistered() {
        SharedMetricRegistries.setDefault("test");

        SslConfiguration sslConfig = SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks"));
        ClientConfiguration conf = ClientConfigurations.of(
                ImmutableList.of("http://localhost"),
                SslSocketFactories.createSslSocketFactory(sslConfig),
                SslSocketFactories.createX509TrustManager(sslConfig));
        OkHttpClients.create(conf, "test", OkHttpClients.class);

        SortedMap<String, Meter> meters = SharedMetricRegistries.getDefault().getMeters();

        assertThat(meters.get("com.palantir.remoting3.okhttp.OkHttpClients.response.family.informational")).isNotNull();
        assertThat(meters.get("com.palantir.remoting3.okhttp.OkHttpClients.response.family.successful")).isNotNull();
        assertThat(meters.get("com.palantir.remoting3.okhttp.OkHttpClients.response.family.redirection")).isNotNull();
        assertThat(meters.get("com.palantir.remoting3.okhttp.OkHttpClients.response.family.client-error")).isNotNull();
        assertThat(meters.get("com.palantir.remoting3.okhttp.OkHttpClients.response.family.server-error")).isNotNull();
    }
}
