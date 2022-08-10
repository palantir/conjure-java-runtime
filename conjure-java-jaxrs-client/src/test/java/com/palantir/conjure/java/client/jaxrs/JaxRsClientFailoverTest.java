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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public final class JaxRsClientFailoverTest extends TestBase {

    private static final int CACHE_DURATION = 2000;

    private static final class PinStrategies implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext _context) {
            FailoverTestCase pinNoCache = new FailoverTestCase(
                    new MockWebServer(), new MockWebServer(), 0, NodeSelectionStrategy.PIN_UNTIL_ERROR);
            FailoverTestCase pinWithCache = new FailoverTestCase(
                    new MockWebServer(), new MockWebServer(), CACHE_DURATION, NodeSelectionStrategy.PIN_UNTIL_ERROR);
            return Stream.of(Arguments.of(pinNoCache), Arguments.of(pinWithCache));
        }
    }

    private static final class AllStrategies implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext _context) {
            FailoverTestCase pinNoCache = new FailoverTestCase(
                    new MockWebServer(), new MockWebServer(), 0, NodeSelectionStrategy.PIN_UNTIL_ERROR);
            FailoverTestCase pinWithCache = new FailoverTestCase(
                    new MockWebServer(), new MockWebServer(), CACHE_DURATION, NodeSelectionStrategy.PIN_UNTIL_ERROR);
            FailoverTestCase roundRobin = new FailoverTestCase(
                    new MockWebServer(), new MockWebServer(), CACHE_DURATION, NodeSelectionStrategy.ROUND_ROBIN);

            return Stream.of(Arguments.of(pinNoCache), Arguments.of(pinWithCache), Arguments.of(roundRobin));
        }
    }

    @ParameterizedTest
    @ArgumentsSource(AllStrategies.class)
    public void testConnectionError_performsFailover(FailoverTestCase failoverTestCase) throws IOException {
        TestService proxy = failoverTestCase.getProxy();

        failoverTestCase.server1.shutdown();
        failoverTestCase.server2.enqueue(new MockResponse().setBody("\"foo\""));

        assertThat(proxy.string()).isEqualTo("foo");
    }

    @ParameterizedTest
    @ArgumentsSource(AllStrategies.class)
    public void testConnectionError_performsFailover_concurrentRequests(FailoverTestCase failoverTestCase)
            throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        TestService proxy = failoverTestCase.getProxy();

        failoverTestCase.server1.shutdown();
        for (int i = 0; i < 10; i++) {
            failoverTestCase.server2.enqueue(new MockResponse().setBody("\"foo\""));
        }

        List<Future<String>> things = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            things.add(executorService.submit(proxy::string));
        }
        for (int i = 0; i < 10; i++) {
            assertThat(things.get(i).get()).isEqualTo("foo");
        }
    }

    @ParameterizedTest
    @ArgumentsSource(AllStrategies.class)
    public void testConnectionError_whenOneCallFailsThenSubsequentNewCallsCanStillSucceed(
            FailoverTestCase failoverTestCase) throws Exception {
        TestService proxy = failoverTestCase.getProxy();

        // Call fails when servers are down.
        failoverTestCase.server1.shutdown();
        failoverTestCase.server2.shutdown();

        assertThatThrownBy(proxy::string).hasRootCauseInstanceOf(ConnectException.class);

        // Subsequent call (with the same proxy instance) succeeds.
        MockWebServer anotherServer1 = new MockWebServer(); // Not a @Rule so we can control start/stop/port explicitly
        anotherServer1.start(failoverTestCase.server1.getPort());
        anotherServer1.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(proxy.string()).isEqualTo("foo");
        anotherServer1.shutdown();
    }

    @ParameterizedTest
    @ArgumentsSource(PinStrategies.class)
    public void testQosError_performsFailover(FailoverTestCase failoverTestCase) throws Exception {
        TestService proxy = failoverTestCase.getProxy();

        failoverTestCase.server1.enqueue(new MockResponse().setResponseCode(503));
        failoverTestCase.server1.enqueue(new MockResponse().setBody("\"foo\""));
        failoverTestCase.server2.enqueue(new MockResponse().setBody("\"bar\""));

        assertThat(proxy.string()).isEqualTo("bar");
    }

    @ParameterizedTest
    @ArgumentsSource(AllStrategies.class)
    public void testConnectionError_performsFailoverOnDnsFailure(FailoverTestCase failoverTestCase) throws Exception {
        failoverTestCase.server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService bogusHostProxy = JaxRsClient.create(
                TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                ClientConfiguration.builder()
                        .from(createTestConfig(
                                "http://foo-bar-bogus-host.unresolvable:80",
                                "http://localhost:" + failoverTestCase.server1.getPort()))
                        .maxNumRetries(2)
                        .build());
        assertThat(bogusHostProxy.string()).isEqualTo("foo");
        assertThat(failoverTestCase.server1.getRequestCount()).isEqualTo(1);
    }

    @Test
    public void testQosError_performsRetryWithOneNode() throws Exception {
        MockWebServer server1 = new MockWebServer();
        server1.enqueue(new MockResponse().setResponseCode(503));
        server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService anotherProxy = JaxRsClient.create(
                TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                ClientConfiguration.builder()
                        .from(createTestConfig("http://localhost:" + server1.getPort()))
                        .maxNumRetries(2)
                        .build());

        assertThat(anotherProxy.string()).isEqualTo("foo");
    }

    @Test
    public void testQosError_performsRetryWithOneNodeAndCache() throws Exception {
        MockWebServer server1 = new MockWebServer();
        server1.enqueue(new MockResponse().setResponseCode(503));
        server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService anotherProxy = JaxRsClient.create(
                TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                ClientConfiguration.builder()
                        .from(createTestConfig("http://localhost:" + server1.getPort()))
                        .maxNumRetries(2)
                        .failedUrlCooldown(Duration.ofMillis(CACHE_DURATION))
                        .build());

        assertThat(anotherProxy.string()).isEqualTo("foo");
    }

    @Test
    public void testCache_recovery() throws Exception {
        MockWebServer server1 = new MockWebServer();

        TestService anotherProxy = JaxRsClient.create(
                TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                ClientConfiguration.builder()
                        .from(createTestConfig("http://localhost:" + server1.getPort()))
                        .maxNumRetries(1)
                        .failedUrlCooldown(Duration.ofMillis(CACHE_DURATION))
                        .build());

        server1.shutdown();

        // Fail the request, ensuring that the URL is added to the cache
        assertThatThrownBy(anotherProxy::string).hasRootCauseInstanceOf(ConnectException.class);

        // Allow the cache to clear
        Thread.sleep(2 * CACHE_DURATION);

        MockWebServer anotherServer1 = new MockWebServer(); // Not a @Rule so we can control start/stop/port explicitly
        anotherServer1.start(server1.getPort());

        anotherServer1.enqueue(new MockResponse().setResponseCode(503));
        anotherServer1.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(anotherProxy.string()).isEqualTo("foo");
        anotherServer1.shutdown();
    }

    private static class FailoverTestCase {
        private final MockWebServer server1;
        private final MockWebServer server2;
        private final long duration;
        private final NodeSelectionStrategy nodeSelectionStrategy;

        FailoverTestCase(
                MockWebServer server1,
                MockWebServer server2,
                long duration,
                NodeSelectionStrategy nodeSelectionStrategy) {
            this.server1 = server1;
            this.server2 = server2;
            this.duration = duration;
            this.nodeSelectionStrategy = nodeSelectionStrategy;
        }

        public TestService getProxy() {
            return JaxRsClient.create(
                    TestService.class,
                    AGENT,
                    new HostMetricsRegistry(),
                    ClientConfiguration.builder()
                            .from(createTestConfig(
                                    "http://localhost:" + server1.getPort(), "http://localhost:" + server2.getPort()))
                            .maxNumRetries(2)
                            .nodeSelectionStrategy(nodeSelectionStrategy)
                            .failedUrlCooldown(Duration.ofMillis(duration))
                            .build());
        }
    }
}
