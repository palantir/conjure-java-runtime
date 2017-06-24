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

package com.palantir.remoting2.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.remoting2.clients.ClientConfiguration;
import com.palantir.remoting2.ext.refresh.Refreshable;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;


public final class JaxRsClientConfigRefreshTest extends TestBase {

    @Rule
    public final MockWebServer server1 = new MockWebServer();
    @Rule
    public final MockWebServer server2 = new MockWebServer();

    @Test
    public void testConfigRefresh() throws Exception {

        ClientConfiguration config1 = createTestConfig("http://localhost:" + server1.getPort());
        ClientConfiguration config2 = createTestConfig("http://localhost:" + server2.getPort());

        Refreshable<ClientConfiguration> refreshableConfig = Refreshable.of(config1);
        Service proxy = JaxRsClient.create(Service.class, "agent", refreshableConfig);

        // Call 1
        server1.enqueue(new MockResponse().setBody("\"server1\""));
        assertThat(proxy.call()).isEqualTo("server1");
        assertThat(server1.getRequestCount()).isEqualTo(1);
        assertThat(server2.getRequestCount()).isEqualTo(0);

        // Call 2
        server1.enqueue(new MockResponse().setBody("\"server1\""));
        assertThat(proxy.call()).isEqualTo("server1");
        assertThat(server1.getRequestCount()).isEqualTo(2);
        assertThat(server2.getRequestCount()).isEqualTo(0);

        // Switch config
        refreshableConfig.set(config2);

        // Call 3
        server2.enqueue(new MockResponse().setBody("\"server2\""));
        assertThat(proxy.call()).isEqualTo("server2");
        assertThat(server1.getRequestCount()).isEqualTo(2);
        assertThat(server2.getRequestCount()).isEqualTo(1);

        // Call 4
        server2.enqueue(new MockResponse().setBody("\"server2\""));
        assertThat(proxy.call()).isEqualTo("server2");
        assertThat(server1.getRequestCount()).isEqualTo(2);
        assertThat(server2.getRequestCount()).isEqualTo(2);
    }

    @Path("/")
    public interface Service {
        @GET
        String call();
    }
}
