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

    @DataPoint
    public static final FailoverTestCase CASE = new FailoverTestCase(new MockWebServer(), new MockWebServer(), 0);

    @DataPoint
    public static final FailoverTestCase CASE_WITH_CACHE = new FailoverTestCase(new MockWebServer(),
            new MockWebServer(), 500);

    @Test
    @Theory
    public void testConnectionError_performsFailover(FailoverTestCase failoverTestCase) throws IOException {
        failoverTestCase.server1.shutdown();
        failoverTestCase.server2.enqueue(new MockResponse().setBody("\"foo\""));

        assertThat(failoverTestCase.getProxy().string(), is("foo"));
    }

    @Test
    @Theory
    public void testConnectionError_performsFailover_concurrentRequests(FailoverTestCase failoverTestCase)
            throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        failoverTestCase.server1.shutdown();
        for (int i = 0; i < 10; i++) {
            failoverTestCase.server2.enqueue(new MockResponse().setBody("\"foo\""));
        }

        TestService proxy = failoverTestCase.getProxy();
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
    public void testConnectionError_whenOneCallFailsThenSubsequentNewCallsCanStillSucceed(FailoverTestCase
            failoverTestCase)
            throws Exception {
        // Call fails when servers are down.
        failoverTestCase.server1.shutdown();
        failoverTestCase.server2.shutdown();

        TestService proxy = failoverTestCase.getProxy();
        try {
            proxy.string();
            fail();
        } catch (RetryableException e) {
            assertThat(e.getMessage(), startsWith("Failed to complete the request due to an IOException"));
        }

        // Subsequent call (with the same proxy instance) succeeds.
        MockWebServer anotherServer1 = new MockWebServer(); // Not a @Rule so we can control start/stop/port explicitly
        anotherServer1.start(failoverTestCase.server1.getPort());
        anotherServer1.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(proxy.string(), is("foo"));
        anotherServer1.shutdown();
    }

    @Test
    @Theory
    public void testQosError_performsFailover(FailoverTestCase failoverTestCase) throws Exception {
        failoverTestCase.server1.enqueue(new MockResponse().setResponseCode(503));
        failoverTestCase.server1.enqueue(new MockResponse().setBody("\"foo\""));
        failoverTestCase.server2.enqueue(new MockResponse().setBody("\"bar\""));

        assertThat(failoverTestCase.getProxy().string(), is("bar"));
    }

    @Test
    @Theory
    public void testConnectionError_performsFailoverOnDnsFailure(FailoverTestCase failoverTestCase)
            throws Exception {
        failoverTestCase.server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService bogusHostProxy = JaxRsClient.create(TestService.class, AGENT,
                ClientConfiguration.builder()
                        .from(createTestConfig(
                                "http://foo-bar-bogus-host.unresolvable:80",
                                "http://localhost:" + failoverTestCase.server1.getPort()))
                        .maxNumRetries(2)
                        .build());
        assertThat(bogusHostProxy.string(), is("foo"));
        assertThat(failoverTestCase.server1.getRequestCount(), is(1));
    }

    @Test
    public void testQosError_performsRetryWithOneNode() throws Exception {
        MockWebServer server1 = new MockWebServer();
        server1.enqueue(new MockResponse().setResponseCode(503));
        server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService anotherProxy = JaxRsClient.create(TestService.class, AGENT, ClientConfiguration.builder()
                .from(createTestConfig("http://localhost:" + server1.getPort()))
                .maxNumRetries(2)
                .build());

        assertThat(anotherProxy.string(), is("foo"));
    }

    @Test
    public void testQosError_performsRetryWithOneNodeAndCache() throws Exception {
        MockWebServer server1 = new MockWebServer();
        server1.enqueue(new MockResponse().setResponseCode(503));
        server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService anotherProxy = JaxRsClient.create(TestService.class, AGENT, ClientConfiguration.builder()
                .from(createTestConfig("http://localhost:" + server1.getPort()))
                .maxNumRetries(2)
                .failedUrlCooldown(Duration.ofMillis(500))
                .build());

        assertThat(anotherProxy.string(), is("foo"));
    }

    private static class FailoverTestCase {
        private final MockWebServer server1;
        private final MockWebServer server2;
        private final long duration;

        FailoverTestCase(MockWebServer server1, MockWebServer server2, long duration) {
            this.server1 = server1;
            this.server2 = server2;
            this.duration = duration;
        }

        public TestService getProxy() {
            return JaxRsClient.create(TestService.class, AGENT,
                    ClientConfiguration.builder()
                            .from(createTestConfig(
                                    "http://localhost:" + server1.getPort(),
                                    "http://localhost:" + server2.getPort()))
                            .maxNumRetries(2)
                            .failedUrlCooldown(Duration.ofMillis(duration))
                            .build());
        }
    }
}
