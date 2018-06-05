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

package com.palantir.remoting3.retrofit2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.remoting3.clients.ClientConfiguration;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@RunWith(Theories.class)
public final class Retrofit2ClientFailoverTest extends TestBase {

    private static final MockWebServer server1 = new MockWebServer();
    private static final MockWebServer server2 = new MockWebServer();

    @DataPoint
    public static final TestService CLIENT = Retrofit2Client.create(
                TestService.class, AGENT,
                ClientConfiguration.builder()
                        .from(createTestConfig(
                                String.format("http://%s:%s/api/", server1.getHostName(), server1.getPort()),
                                String.format("http://%s:%s/api/", server2.getHostName(), server2.getPort())))
                        .maxNumRetries(2)  // need 2 retries because URL order is not deterministic
                        .build());

    @DataPoint
    public static final TestService CLIENT_WITH_COOLDOWN = Retrofit2Client.create(
            TestService.class, AGENT,
            ClientConfiguration.builder()
                    .from(createTestConfig(
                            String.format("http://%s:%s/api/", server1.getHostName(), server1.getPort()),
                            String.format("http://%s:%s/api/", server2.getHostName(), server2.getPort())))
                    .maxNumRetries(2)  // need 2 retries because URL order is not deterministic
                    .failedUrlCooldown(Duration.ofMillis(1000))
                    .build());

    @Test
    @Theory
    public void testConnectionError_performsFailover(TestService proxy) throws IOException {
        server1.shutdown();
        server2.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(proxy.get().execute().body()).isEqualTo("pong");
    }

    @Test
    @Theory
    public void testConnectionError_whenOneCallFailsThenSubsequentNewCallsCanStillSucceed(TestService proxy)
            throws Exception {
        server1.shutdown();
        server2.shutdown();

        assertThatThrownBy(() -> proxy.get().execute())
                .isInstanceOf(IOException.class)
                .hasMessageStartingWith("Failed to complete the request due to an IOException");

        // Subsequent call (with the same proxy instance) succeeds.
        MockWebServer anotherServer1 = new MockWebServer(); // Not a @Rule so we can control start/stop/port explicitly
        anotherServer1.start(server1.getPort());
        anotherServer1.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(proxy.get().execute().body()).isEqualTo("foo");
        anotherServer1.shutdown();
    }

    @Test
    @Theory
    public void testQosError_performsFailover_forSynchronousOperation(TestService proxy) throws Exception {
        server1.enqueue(new MockResponse().setResponseCode(503));
        server1.enqueue(new MockResponse().setBody("\"foo\""));
        server2.enqueue(new MockResponse().setBody("\"bar\""));

        assertThat(proxy.get().execute().body()).isEqualTo("bar");
    }

    @Test
    @Theory
    public void testQosError_performsFailover_forAsynchronousOperation(TestService proxy) throws Exception {
        server1.enqueue(new MockResponse().setResponseCode(503));
        server1.enqueue(new MockResponse().setBody("\"foo\""));
        server2.enqueue(new MockResponse().setBody("\"bar\""));

        CompletableFuture<String> future = new CompletableFuture<>();
        proxy.get().enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                future.complete(response.body());
            }

            @Override
            public void onFailure(Call<String> call, Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        assertThat(future.get()).isEqualTo("bar");
    }

    @Test
    public void testConnectionError_performsFailoverOnDnsFailure() throws Exception {
        server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService bogusHostProxy = Retrofit2Client.create(TestService.class, AGENT,
                ClientConfiguration.builder()
                        .from(createTestConfig(
                                "http://foo-bar-bogus-host.unresolvable:80",
                                "http://localhost:" + server1.getPort()))
                        .maxNumRetries(2)
                        .build());
        assertThat(bogusHostProxy.get().execute().body()).isEqualTo("foo");
        assertThat(server1.getRequestCount()).isEqualTo(1);
    }

    @Test
    public void testQosError_performsRetryWithOneNode_forSynchronousOperation() throws Exception {
        server1.enqueue(new MockResponse().setResponseCode(503));
        server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService anotherProxy = Retrofit2Client.create(TestService.class, AGENT, ClientConfiguration.builder()
                .from(createTestConfig("http://localhost:" + server1.getPort()))
                .maxNumRetries(2)
                .build());

        assertThat(anotherProxy.get().execute().body()).isEqualTo("foo");
    }

    @Test
    public void testQosError_performsRetryWithOneNode_forAsynchronousOperation() throws Exception {
        server1.enqueue(new MockResponse().setResponseCode(503));
        server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService anotherProxy = Retrofit2Client.create(TestService.class, AGENT, ClientConfiguration.builder()
                .from(createTestConfig("http://localhost:" + server1.getPort()))
                .maxNumRetries(2)
                .build());

        CompletableFuture<String> future = new CompletableFuture<>();
        anotherProxy.get().enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                future.complete(response.body());
            }

            @Override
            public void onFailure(Call<String> call, Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });

        assertThat(future.get()).isEqualTo("foo");
    }
}
