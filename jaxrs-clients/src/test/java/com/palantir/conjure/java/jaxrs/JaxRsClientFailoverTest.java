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

package com.palantir.conjure.java.jaxrs;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.Lists;
import com.palantir.conjure.java.clients.NodeSelectionStrategy;
import com.palantir.conjure.java.clients.ClientConfiguration;
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
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public final class JaxRsClientFailoverTest extends TestBase {

    private static final int CACHE_DURATION = 2000;

    @DataPoints("PinStrategies")
    public static FailoverTestCase[] pinStrategies() {
        FailoverTestCase pinNoCache = new FailoverTestCase(new MockWebServer(), new MockWebServer(), 0,
                NodeSelectionStrategy.PIN_UNTIL_ERROR);
        FailoverTestCase pinWithCache = new FailoverTestCase(new MockWebServer(), new MockWebServer(), CACHE_DURATION,
                NodeSelectionStrategy.PIN_UNTIL_ERROR);
        return new FailoverTestCase[]{pinNoCache, pinWithCache};
    }

    @DataPoints("AllStrategies")
    public static FailoverTestCase[] allStrategies() {
        FailoverTestCase pinNoCache = new FailoverTestCase(new MockWebServer(), new MockWebServer(), 0,
                NodeSelectionStrategy.PIN_UNTIL_ERROR);
        FailoverTestCase pinWithCache = new FailoverTestCase(new MockWebServer(), new MockWebServer(), CACHE_DURATION,
                NodeSelectionStrategy.PIN_UNTIL_ERROR);
        FailoverTestCase roundRobin = new FailoverTestCase(new MockWebServer(), new MockWebServer(), CACHE_DURATION,
                NodeSelectionStrategy.ROUND_ROBIN);
        return new FailoverTestCase[]{pinNoCache, pinWithCache, roundRobin};
    }

    @Test
    @Theory
    public void testConnectionError_performsFailover(
            @FromDataPoints("AllStrategies") FailoverTestCase failoverTestCase) throws IOException {
        TestService proxy = failoverTestCase.getProxy();

        failoverTestCase.server1.shutdown();
        failoverTestCase.server2.enqueue(new MockResponse().setBody("\"foo\""));

        assertThat(proxy.string(), is("foo"));
    }

    @Test
    @Theory
    public void testConnectionError_performsFailover_concurrentRequests(
            @FromDataPoints("AllStrategies") FailoverTestCase failoverTestCase) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        TestService proxy = failoverTestCase.getProxy();

        failoverTestCase.server1.shutdown();
        for (int i = 0; i < 10; i++) {
            failoverTestCase.server2.enqueue(new MockResponse().setBody("\"foo\""));
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
    public void testConnectionError_whenOneCallFailsThenSubsequentNewCallsCanStillSucceed(
            @FromDataPoints("AllStrategies") FailoverTestCase failoverTestCase) throws Exception {
        TestService proxy = failoverTestCase.getProxy();

        // Call fails when servers are down.
        failoverTestCase.server1.shutdown();
        failoverTestCase.server2.shutdown();

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
    public void testQosError_performsFailover(
            @FromDataPoints("PinStrategies")  FailoverTestCase failoverTestCase) throws Exception {
        TestService proxy = failoverTestCase.getProxy();

        failoverTestCase.server1.enqueue(new MockResponse().setResponseCode(503));
        failoverTestCase.server1.enqueue(new MockResponse().setBody("\"foo\""));
        failoverTestCase.server2.enqueue(new MockResponse().setBody("\"bar\""));

        assertThat(proxy.string(), is("bar"));
    }

    @Test
    @Theory
    public void testConnectionError_performsFailoverOnDnsFailure(
            @FromDataPoints("AllStrategies") FailoverTestCase failoverTestCase) throws Exception {
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
                .failedUrlCooldown(Duration.ofMillis(CACHE_DURATION))
                .build());

        assertThat(anotherProxy.string(), is("foo"));
    }

    @Test
    public void testCache_recovery() throws Exception {
        MockWebServer server1 = new MockWebServer();

        TestService anotherProxy = JaxRsClient.create(TestService.class, AGENT, ClientConfiguration.builder()
                .from(createTestConfig("http://localhost:" + server1.getPort()))
                .maxNumRetries(1)
                .failedUrlCooldown(Duration.ofMillis(CACHE_DURATION))
                .build());

        server1.shutdown();

        // Fail the request, ensuring that the URL is added to the cache
        assertThatExceptionOfType(RetryableException.class).isThrownBy(() -> anotherProxy.string());

        // Allow the cache to clear
        Thread.sleep(2 * CACHE_DURATION);

        MockWebServer anotherServer1 = new MockWebServer(); // Not a @Rule so we can control start/stop/port explicitly
        anotherServer1.start(server1.getPort());

        anotherServer1.enqueue(new MockResponse().setResponseCode(503));
        anotherServer1.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(anotherProxy.string(), is("foo"));
        anotherServer1.shutdown();
    }

    @Test
    public void testPerformsRoundRobin() throws Exception {
        FailoverTestCase failoverTestCase = new FailoverTestCase(new MockWebServer(),
                new MockWebServer(), CACHE_DURATION, NodeSelectionStrategy.ROUND_ROBIN);

        TestService proxy = failoverTestCase.getProxy();
        failoverTestCase.server1.enqueue(new MockResponse().setBody("\"foo\""));
        failoverTestCase.server2.enqueue(new MockResponse().setBody("\"bar\""));

        proxy.string();
        proxy.string();

        assertThat(failoverTestCase.server1.getRequestCount(), is(1));
        assertThat(failoverTestCase.server2.getRequestCount(), is(1));
    }

    private static class FailoverTestCase {
        private final MockWebServer server1;
        private final MockWebServer server2;
        private final long duration;
        private final NodeSelectionStrategy nodeSelectionStrategy;

        FailoverTestCase(MockWebServer server1, MockWebServer server2, long duration,
                NodeSelectionStrategy nodeSelectionStrategy) {
            this.server1 = server1;
            this.server2 = server2;
            this.duration = duration;
            this.nodeSelectionStrategy = nodeSelectionStrategy;
        }

        public TestService getProxy() {
            return JaxRsClient.create(TestService.class, AGENT,
                    ClientConfiguration.builder()
                            .from(createTestConfig(
                                    "http://localhost:" + server1.getPort(),
                                    "http://localhost:" + server2.getPort()))
                            .maxNumRetries(2)
                            .nodeSelectionStrategy(nodeSelectionStrategy)
                            .failedUrlCooldown(Duration.ofMillis(duration))
                            .build());
        }
    }
}
