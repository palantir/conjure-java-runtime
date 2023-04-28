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

package com.palantir.conjure.java.client.retrofit2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class Retrofit2ClientFailoverTest extends TestBase {

    private static final int CACHE_DURATION = 2000;

    private static Stream<FailoverTestCase> pinStrategies() {
        FailoverTestCase pinNoCache = new FailoverTestCase(
                new MockWebServer(), new MockWebServer(), 0, NodeSelectionStrategy.PIN_UNTIL_ERROR);
        FailoverTestCase pinWithCache = new FailoverTestCase(
                new MockWebServer(), new MockWebServer(), CACHE_DURATION, NodeSelectionStrategy.PIN_UNTIL_ERROR);
        return Stream.of(pinNoCache, pinWithCache);
    }

    private static Stream<FailoverTestCase> allStrategies() {
        FailoverTestCase pinNoCache = new FailoverTestCase(
                new MockWebServer(), new MockWebServer(), 0, NodeSelectionStrategy.PIN_UNTIL_ERROR);
        FailoverTestCase pinWithCache = new FailoverTestCase(
                new MockWebServer(), new MockWebServer(), CACHE_DURATION, NodeSelectionStrategy.PIN_UNTIL_ERROR);
        FailoverTestCase roundRobin = new FailoverTestCase(
                new MockWebServer(), new MockWebServer(), CACHE_DURATION, NodeSelectionStrategy.ROUND_ROBIN);
        return Stream.of(pinNoCache, pinWithCache, roundRobin);
    }

    @ParameterizedTest
    @MethodSource("allStrategies")
    public void testConnectionError_performsFailover(FailoverTestCase failoverTestCase) throws IOException {
        TestService proxy = failoverTestCase.getProxy();

        failoverTestCase.server1.shutdown();
        failoverTestCase.server2.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(proxy.get().execute().body()).isEqualTo("pong");
    }

    @ParameterizedTest
    @MethodSource("allStrategies")
    public void testConnectionError_whenOneCallFailsThenSubsequentNewCallsCanStillSucceed(
            FailoverTestCase failoverTestCase) throws Exception {
        TestService proxy = failoverTestCase.getProxy();

        failoverTestCase.server1.shutdown();
        failoverTestCase.server2.shutdown();

        assertThatThrownBy(() -> proxy.get().execute())
                .isInstanceOf(IOException.class)
                .hasMessageStartingWith("Failed to complete the request due to an IOException");

        // Subsequent call (with the same proxy instance) succeeds.
        try (MockWebServer anotherServer1 = new MockWebServer()) {
            // Not a @Rule so we can control start/stop/port explicitly
            anotherServer1.start(failoverTestCase.server1.getPort());
            anotherServer1.enqueue(new MockResponse().setBody("\"foo\""));
            assertThat(proxy.get().execute().body()).isEqualTo("foo");
            anotherServer1.shutdown();
        }
    }

    @ParameterizedTest
    @MethodSource("pinStrategies")
    public void testQosError_performsFailover_forSynchronousOperation(FailoverTestCase failoverTestCase)
            throws Exception {
        TestService proxy = failoverTestCase.getProxy();

        failoverTestCase.server1.enqueue(new MockResponse().setResponseCode(503));
        failoverTestCase.server1.enqueue(new MockResponse().setBody("\"foo\""));
        failoverTestCase.server2.enqueue(new MockResponse().setBody("\"bar\""));

        assertThat(proxy.get().execute().body()).isEqualTo("bar");
    }

    @ParameterizedTest
    @MethodSource("pinStrategies")
    public void testQosError_performsFailover_forAsynchronousOperation(FailoverTestCase failoverTestCase)
            throws Exception {
        TestService proxy = failoverTestCase.getProxy();

        failoverTestCase.server1.enqueue(new MockResponse().setResponseCode(503));
        failoverTestCase.server1.enqueue(new MockResponse().setBody("\"foo\""));
        failoverTestCase.server2.enqueue(new MockResponse().setBody("\"bar\""));

        CompletableFuture<String> future = new CompletableFuture<>();
        proxy.get().enqueue(new Callback<>() {
            @Override
            public void onResponse(@Nonnull Call<String> _call, @Nonnull Response<String> response) {
                future.complete(response.body());
            }

            @Override
            public void onFailure(@Nonnull Call<String> _call, @Nonnull Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        assertThat(future.get()).isEqualTo("bar");
    }

    @ParameterizedTest
    @MethodSource("allStrategies")
    public void testConnectionError_performsFailoverOnDnsFailure(FailoverTestCase failoverTestCase) throws Exception {
        failoverTestCase.server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService bogusHostProxy = Retrofit2Client.create(
                TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                ClientConfiguration.builder()
                        .from(createTestConfig(
                                "http://foo-bar-bogus-host.unresolvable:80",
                                "http://localhost:" + failoverTestCase.server1.getPort()))
                        .maxNumRetries(2)
                        .build());
        assertThat(bogusHostProxy.get().execute().body()).isEqualTo("foo");
        assertThat(failoverTestCase.server1.getRequestCount()).isEqualTo(1);
    }

    @Test
    public void testQosError_performsRetryWithOneNode_forSynchronousOperation() throws Exception {
        try (MockWebServer server1 = new MockWebServer()) {
            server1.enqueue(new MockResponse().setResponseCode(503));
            server1.enqueue(new MockResponse().setBody("\"foo\""));

            TestService anotherProxy = Retrofit2Client.create(
                    TestService.class,
                    AGENT,
                    new HostMetricsRegistry(),
                    ClientConfiguration.builder()
                            .from(createTestConfig("http://localhost:" + server1.getPort()))
                            .maxNumRetries(2)
                            .build());
            assertThat(anotherProxy.get().execute().body()).isEqualTo("foo");
        }
    }

    @Test
    public void testQosError_performsRetryWithOneNodeAndCache_forSynchronousOperation() throws Exception {
        try (MockWebServer server1 = new MockWebServer()) {
            server1.enqueue(new MockResponse().setResponseCode(503));
            server1.enqueue(new MockResponse().setBody("\"foo\""));

            TestService anotherProxy = Retrofit2Client.create(
                    TestService.class,
                    AGENT,
                    new HostMetricsRegistry(),
                    ClientConfiguration.builder()
                            .from(createTestConfig("http://localhost:" + server1.getPort()))
                            .maxNumRetries(2)
                            .failedUrlCooldown(Duration.ofMillis(CACHE_DURATION))
                            .build());

            assertThat(anotherProxy.get().execute().body()).isEqualTo("foo");
        }
    }

    @Test
    public void testQosError_performsRetryWithOneNode_forAsynchronousOperation() throws Exception {
        try (MockWebServer server1 = new MockWebServer()) {
            server1.enqueue(new MockResponse().setResponseCode(503));
            server1.enqueue(new MockResponse().setBody("\"foo\""));

            TestService anotherProxy = Retrofit2Client.create(
                    TestService.class,
                    AGENT,
                    new HostMetricsRegistry(),
                    ClientConfiguration.builder()
                            .from(createTestConfig("http://localhost:" + server1.getPort()))
                            .maxNumRetries(2)
                            .build());

            CompletableFuture<String> future = new CompletableFuture<>();
            anotherProxy.get().enqueue(new Callback<>() {
                @Override
                public void onResponse(@Nonnull Call<String> _call, @Nonnull Response<String> response) {
                    future.complete(response.body());
                }

                @Override
                public void onFailure(@Nonnull Call<String> _call, @Nonnull Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });

            assertThat(future.get()).isEqualTo("foo");
        }
    }

    @Test
    public void testQosError_performsRetryWithOneNodeAndCache_forAsynchronousOperation() throws Exception {
        try (MockWebServer server1 = new MockWebServer()) {
            server1.enqueue(new MockResponse().setResponseCode(503));
            server1.enqueue(new MockResponse().setBody("\"foo\""));

            TestService anotherProxy = Retrofit2Client.create(
                    TestService.class,
                    AGENT,
                    new HostMetricsRegistry(),
                    ClientConfiguration.builder()
                            .from(createTestConfig("http://localhost:" + server1.getPort()))
                            .maxNumRetries(2)
                            .failedUrlCooldown(Duration.ofMillis(CACHE_DURATION))
                            .build());

            CompletableFuture<String> future = new CompletableFuture<>();
            anotherProxy.get().enqueue(new Callback<>() {
                @Override
                public void onResponse(@Nonnull Call<String> _call, @Nonnull Response<String> response) {
                    future.complete(response.body());
                }

                @Override
                public void onFailure(@Nonnull Call<String> _call, @Nonnull Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });

            assertThat(future.get()).isEqualTo("foo");
        }
    }

    @Test
    public void testCache_recovery() throws Exception {
        try (MockWebServer server1 = new MockWebServer()) {

            TestService anotherProxy = Retrofit2Client.create(
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
            assertThatThrownBy(() -> anotherProxy.get().execute())
                    .isInstanceOf(IOException.class)
                    .hasMessageStartingWith("Failed to complete the request due to an IOException");

            // Allow the cache to clear
            Thread.sleep(2 * CACHE_DURATION);

            try (MockWebServer anotherServer1 = new MockWebServer()) {
                // Not a @Rule so we can control start/stop/port explicitly
                anotherServer1.start(server1.getPort());

                anotherServer1.enqueue(new MockResponse().setResponseCode(503));
                anotherServer1.enqueue(new MockResponse().setBody("\"foo\""));
                assertThat(anotherProxy.get().execute().body()).isEqualTo("foo");
                anotherServer1.shutdown();
            }
        }
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
            return Retrofit2Client.create(
                    TestService.class,
                    AGENT,
                    new HostMetricsRegistry(),
                    ClientConfiguration.builder()
                            .from(createTestConfig(
                                    String.format("http://%s:%s/api/", server1.getHostName(), server1.getPort()),
                                    String.format("http://%s:%s/api/", server2.getHostName(), server2.getPort())))
                            .maxNumRetries(2) // need 2 retries because URL order is not deterministic
                            .nodeSelectionStrategy(nodeSelectionStrategy)
                            .failedUrlCooldown(Duration.ofMillis(duration))
                            .build());
        }
    }
}
