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

package com.palantir.remoting3.jaxrs;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.Lists;
import com.palantir.remoting3.clients.ClientConfiguration;
import feign.RetryableException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public final class JaxRsClientFailoverTest extends TestBase {

    private static final MockWebServer server1 = new MockWebServer();
    private static final MockWebServer server2 = new MockWebServer();

    @DataPoint
    public static final TestService CLIENT = JaxRsClient.create(TestService.class, AGENT,
                ClientConfiguration.builder()
                        .from(createTestConfig(
                                "http://localhost:" + server1.getPort(),
                                "http://localhost:" + server2.getPort()))
                        .maxNumRetries(2)
                        .build());

    @DataPoint
    public static final TestService CLIENT_WITH_COOLDOWN = JaxRsClient.create(TestService.class, AGENT,
            ClientConfiguration.builder()
                    .from(createTestConfig(
                            "http://localhost:" + server1.getPort(),
                            "http://localhost:" + server2.getPort()))
                    .maxNumRetries(2)
                    .failedUrlCooldown(Duration.ofMillis(50))
                    .build());

    @Test
    @Theory
    public void testConnectionError_performsFailover(TestService proxy) throws IOException {
        server1.shutdown();
        server2.enqueue(new MockResponse().setBody("\"foo\""));

        assertThat(proxy.string(), is("foo"));
    }

    @Test
    @Theory
    public void testConnectionError_performsFailover_concurrentRequests(TestService proxy) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        server1.shutdown();
        for (int i = 0; i < 10; i++) {
            server2.enqueue(new MockResponse().setBody("\"foo\""));
        }

        List<Future<String>> things = Lists.newArrayListWithCapacity(10);
        for (int i = 0; i < 10; i++) {
            things.add(executorService.submit(() -> proxy.string()));
        }
        for (int i = 0; i < 10; i++) {
            assertThat(things.get(i).get(), is("foo"));
        }
    }

    @Test
    @Theory
    public void testConnectionError_whenOneCallFailsThenSubsequentNewCallsCanStillSucceed(TestService proxy)
            throws Exception {
        // Call fails when servers are down.
        server1.shutdown();
        server2.shutdown();
        try {
            proxy.string();
            fail();
        } catch (RetryableException e) {
            assertThat(e.getMessage(), startsWith("Failed to complete the request due to an IOException"));
        }

        // Subsequent call (with the same proxy instance) succeeds.
        MockWebServer anotherServer1 = new MockWebServer(); // Not a @Rule so we can control start/stop/port explicitly
        anotherServer1.start(server1.getPort());
        anotherServer1.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(proxy.string(), is("foo"));
        anotherServer1.shutdown();
    }

    @Test
    @Theory
    public void testQosError_performsFailover(TestService proxy) throws Exception {
        server1.enqueue(new MockResponse().setResponseCode(503));
        server1.enqueue(new MockResponse().setBody("\"foo\""));
        server2.enqueue(new MockResponse().setBody("\"bar\""));

        assertThat(proxy.string(), is("bar"));
    }

    @Test
    public void testConnectionError_performsFailoverOnDnsFailure() throws Exception {
        server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService bogusHostProxy = JaxRsClient.create(TestService.class, AGENT,
                ClientConfiguration.builder()
                        .from(createTestConfig(
                                "http://foo-bar-bogus-host.unresolvable:80",
                                "http://localhost:" + server1.getPort()))
                        .maxNumRetries(2)
                        .build());
        assertThat(bogusHostProxy.string(), is("foo"));
        assertThat(server1.getRequestCount(), is(1));
    }

    @Test
    public void testQosError_performsRetryWithOneNode() throws Exception {
        server1.enqueue(new MockResponse().setResponseCode(503));
        server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService anotherProxy = JaxRsClient.create(TestService.class, AGENT, ClientConfiguration.builder()
                .from(createTestConfig("http://localhost:" + server1.getPort()))
                .maxNumRetries(2)
                .build());

        assertThat(anotherProxy.string(), is("foo"));
    }
}
