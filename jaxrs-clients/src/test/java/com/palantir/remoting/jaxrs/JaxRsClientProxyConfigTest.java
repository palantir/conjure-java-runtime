/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.config.service.BasicCredentials;
import com.palantir.config.service.ProxyConfiguration;
import com.palantir.remoting.clients.ClientConfig;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.Test;

public final class JaxRsClientProxyConfigTest {

    @Rule
    public final MockWebServer server = new MockWebServer();
    @Rule
    public final MockWebServer proxyServer = new MockWebServer();

    @Test
    public void testDirectVersusProxyConnection() throws Exception {
        server.enqueue(new MockResponse().setBody("\"server\""));
        proxyServer.enqueue(new MockResponse().setBody("\"proxyServer\""));

        FakeoInterface directService = JaxRsClient.builder()
                .build(FakeoInterface.class, "agent", "http://localhost:" + server.getPort());
        ProxyConfiguration proxyConfiguration = ProxyConfiguration.of("localhost:" + proxyServer.getPort());
        FakeoInterface proxiedService = JaxRsClient.builder(ClientConfig.builder().proxy(proxyConfiguration).build())
                .build(FakeoInterface.class, "agent", "http://localhost:" + server.getPort());

        assertThat(directService.blah()).isEqualTo("server");
        assertThat(proxiedService.blah()).isEqualTo("proxyServer");
        RecordedRequest proxyRequest = proxyServer.takeRequest();
        assertThat(proxyRequest.getHeader("Host")).isEqualTo("localhost:" + server.getPort());
    }

    @Test
    public void testAuthenticatedProxy() throws Exception {
        proxyServer.enqueue(new MockResponse().setResponseCode(407)); // indicates authenticated proxy
        proxyServer.enqueue(new MockResponse().setBody("\"proxyServer\""));

        ProxyConfiguration authProxyConfig = ProxyConfiguration.of(
                "localhost:" + proxyServer.getPort(),
                BasicCredentials.of("fakeUser", "fakePassword"));
        FakeoInterface authClient = JaxRsClient.builder(ClientConfig.builder().proxy(authProxyConfig).build())
                .build(FakeoInterface.class, "agent", "http://localhost:" + server.getPort());

        assertThat(authClient.blah()).isEqualTo("proxyServer");
        RecordedRequest firstRequest = proxyServer.takeRequest();
        assertThat(firstRequest.getHeader("Proxy-Authorization")).isNull();
        RecordedRequest secondRequest = proxyServer.takeRequest();
        assertThat(secondRequest.getHeader("Proxy-Authorization")).isEqualTo("Basic ZmFrZVVzZXI6ZmFrZVBhc3N3b3Jk");
    }

    @Path("/fakeo")
    public interface FakeoInterface {
        @GET
        String blah();
    }
}
