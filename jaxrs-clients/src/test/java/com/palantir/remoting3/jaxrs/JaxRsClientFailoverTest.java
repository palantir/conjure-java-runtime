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

package com.palantir.remoting3.jaxrs;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import feign.RetryableException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;


public final class JaxRsClientFailoverTest extends TestBase {

    @Rule
    public final MockWebServer server1 = new MockWebServer();
    @Rule
    public final MockWebServer server2 = new MockWebServer();

    @Test
    public void testFailover() throws Exception {
        server1.shutdown();
        server2.enqueue(new MockResponse().setBody("\"foo\""));

        Service proxy = JaxRsClient.create(Service.class, "agent",
                createTestConfig(
                        "http://localhost:" + server1.getPort(),
                        "http://localhost:" + server2.getPort()));
        assertThat(proxy.get(), is("foo"));
    }

    @Test
    public void testConsecutiveCalls() throws Exception {
        Service proxy = JaxRsClient.create(Service.class, "agent",
                createTestConfig(
                        "http://localhost:" + server1.getPort(),
                        "http://localhost:" + server2.getPort()));

        // Call fails when servers are down.
        server1.shutdown();
        server2.shutdown();
        try {
            proxy.get();
            fail();
        } catch (RetryableException e) {
            assertThat(e.getMessage(), startsWith("Could not connect to any of the following servers: "));
        }

        // Subsequent call (with the same proxy instance) succeeds.
        MockWebServer anotherServer1 = new MockWebServer(); // Not a @Rule so we can control start/stop/port explicitly
        anotherServer1.start(server1.getPort());
        anotherServer1.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(proxy.get(), is("foo"));
        anotherServer1.shutdown();
    }

    @Test
    public void testFailoverOnDnsFailure() throws Exception {
        server1.enqueue(new MockResponse().setBody("\"foo\""));

        Service proxy = JaxRsClient.create(Service.class, "agent",
                createTestConfig(
                        "http://foo-bar-bogus-host.unresolvable:80",
                        "http://localhost:" + server1.getPort()));
        assertThat(proxy.get(), is("foo"));
        assertThat(server1.getRequestCount(), is(1));
    }

    @Path("/")
    public interface Service {
        @GET
        String get();
    }
}
