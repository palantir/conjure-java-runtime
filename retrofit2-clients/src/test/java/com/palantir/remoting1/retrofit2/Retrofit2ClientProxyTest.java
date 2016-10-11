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

package com.palantir.remoting1.retrofit2;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.palantir.remoting1.clients.ClientConfig;
import com.palantir.remoting1.config.service.BasicCredentials;
import com.palantir.remoting1.config.service.ProxyConfiguration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.Test;

public final class Retrofit2ClientProxyTest {

    @Rule
    public final MockWebServer server = new MockWebServer();
    @Rule
    public final MockWebServer proxyServer = new MockWebServer();

    @Test
    public void testDirectVersusProxyConnection() throws Exception {
        server.enqueue(new MockResponse().setBody("\"server\""));
        proxyServer.enqueue(new MockResponse().setBody("\"proxyServer\""));

        TestService directService = Retrofit2Client.builder().build(
                TestService.class, "agent", "http://localhost:" + server.getPort());
        ClientConfig clientConfig = ClientConfig.builder()
                .proxy(ProxyConfiguration.of("localhost:" + proxyServer.getPort()))
                .build();
        TestService proxiedService = Retrofit2Client.builder(clientConfig).build(
                TestService.class, "agent", "http://localhost:" + server.getPort());

        assertThat(directService.get().execute().body(), is("server"));
        assertThat(proxiedService.get().execute().body(), is("proxyServer"));
        RecordedRequest proxyRequest = proxyServer.takeRequest();
        assertThat(proxyRequest.getHeader("Host"), is("localhost:" + server.getPort()));
    }

    @Test
    public void testAuthenticatedProxy() throws Exception {
        proxyServer.enqueue(new MockResponse().setResponseCode(407)); // indicates authenticated proxy
        proxyServer.enqueue(new MockResponse().setBody("\"proxyServer\""));

        ClientConfig clientConfig = ClientConfig.builder()
                .proxy(ProxyConfiguration.of(
                        "localhost:" + proxyServer.getPort(), BasicCredentials.of("fakeUser", "fakePassword")))
                .build();

        TestService proxiedService = Retrofit2Client.builder(clientConfig).build(
                TestService.class, "agent", "http://localhost:" + server.getPort());

        assertThat(proxiedService.get().execute().body(), is("proxyServer"));
        RecordedRequest firstRequest = proxyServer.takeRequest();
        assertNull(firstRequest.getHeader("Proxy-Authorization"));
        RecordedRequest secondRequest = proxyServer.takeRequest();
        assertThat(secondRequest.getHeader("Proxy-Authorization"), is("Basic ZmFrZVVzZXI6ZmFrZVBhc3N3b3Jk"));
    }
}
