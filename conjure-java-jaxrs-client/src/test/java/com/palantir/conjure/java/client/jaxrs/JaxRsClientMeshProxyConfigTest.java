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

package com.palantir.conjure.java.client.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;

public final class JaxRsClientMeshProxyConfigTest extends TestBase {

    @Rule public final MockWebServer server = new MockWebServer();

    @Test
    public void meshProxy() throws Exception {
        server.enqueue(new MockResponse().setBody("\"server\""));

        ClientConfiguration proxiedConfig = ClientConfiguration.builder()
                .from(createTestConfig("http://foo.com/"))
                .meshProxy(HostAndPort.fromParts("localhost", server.getPort()))
                .maxNumRetries(0)
                .build();

        TestService proxiedService =
                JaxRsClient.create(TestService.class, AGENT, new HostMetricsRegistry(), proxiedConfig);

        assertThat(proxiedService.string()).isEqualTo("server");
        assertThat(server.takeRequest().getHeader(HttpHeaders.HOST)).isEqualTo("foo.com");
    }
}
