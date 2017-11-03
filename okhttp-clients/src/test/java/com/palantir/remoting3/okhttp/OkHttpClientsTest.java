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

package com.palantir.remoting3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting3.clients.ClientConfiguration;
import com.palantir.remoting3.okhttp.metrics.HostMetrics;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.MockitoAnnotations;

@RunWith(Parameterized.class)
public final class OkHttpClientsTest extends TestBase {

    @Parameterized.Parameters
    public static Collection<CallExecutor> callExecutors() {
        return ImmutableList.of(
                CallExecutor.BLOCKING,
                CallExecutor.ASYNC);
    }

    @Rule
    public final MockWebServer server = new MockWebServer();
    @Rule
    public final MockWebServer server2 = new MockWebServer();

    private String url;
    private String url2;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private CallExecutor callExecutor;

    public OkHttpClientsTest(CallExecutor executor) {
        this.callExecutor = executor;
    }

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        url = "http://localhost:" + server.getPort();
        url2 = "http://localhost:" + server2.getPort();
    }

    private static MetricName name(String family) {
        return MetricName.builder()
                .safeName("OkHttpClientsTest" + HostMetrics.CLIENT_RESPONSE_METRIC_NAME_SUFFIX)
                .putSafeTags(HostMetrics.HOSTNAME_TAG, "localhost")
                .putSafeTags(HostMetrics.FAMILY_TAG, family).build();
    }

    @Test
    public void verifyResponseMetricsAreRegistered() throws IOException {
        TaggedMetricRegistry registry = DefaultTaggedMetricRegistry.getDefault();

        server.enqueue(new MockResponse().setBody("pong"));
        createRetryingClient(1).newCall(new Request.Builder().url(url).build()).execute();

        assertThat(registry.getMetrics().get(name("informational"))).isNotNull();
        assertThat(registry.getMetrics().get(name("successful"))).isNotNull();
        assertThat(registry.getMetrics().get(name("redirection"))).isNotNull();
        assertThat(registry.getMetrics().get(name("client-error"))).isNotNull();
        assertThat(registry.getMetrics().get(name("server-error"))).isNotNull();
    }

    @Test
    public void doesNotHangIfManyCallsResultInExceptions() throws Exception {
        int maxRetries = 50;

        for (int i = 0; i <= maxRetries; i++) {
            server.enqueue(new MockResponse().setResponseCode(503));
        }

        OkHttpClient client = createRetryingClient(maxRetries, Duration.ofMillis(0));

        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(() -> execute(call)).isInstanceOf(QosIoException.class);
    }

    @Test
    public void throwsProperRemoteExceptionAfterRetryForBlockingCalls() throws Exception {
        if (callExecutor == CallExecutor.ASYNC) {
            return; // this test cannot be made to pass with an async call, as Callback requires an IOException
        }

        // first we get a 503
        server.enqueue(new MockResponse().setResponseCode(503));

        // then we get a RemoteException
        SerializableError error = SerializableError.builder().errorCode("error code").errorName("error name").build();
        MockResponse mockResponse = new MockResponse()
                .setBody(new ObjectMapper().writeValueAsString(error))
                .addHeader("Content-Type", "application/json")
                .setResponseCode(400);
        server.enqueue(mockResponse);

        OkHttpClient client = createRetryingClient(5);

        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(() -> execute(call)).isInstanceOf(RemoteException.class);
    }

    @Test
    public void interceptsAndHandlesQosIoExceptions_endToEnd_whenClientRetriesSufficientlyOften() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("pong"));

        Call call = createRetryingClient(2).newCall(new Request.Builder().url(url).build());
        assertThat(execute(call).body().string()).isEqualTo("pong");
        assertThat(server.getRequestCount()).isEqualTo(3 /* original plus two retries */);
    }

    @Test
    public void interceptsAndHandlesQosIoExceptions_endToEnd_whenClientDoesNotRetrySufficientlyOften()
            throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));

        Call call = createRetryingClient(1).newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(() -> execute(call))
                .hasMessage("Failed to complete the request due to a server-side QoS condition: 503")
                .isInstanceOf(QosIoException.class);
        assertThat(server.getRequestCount()).isEqualTo(2 /* original plus one retries */);
    }

    @Test
    public void doesNotShareBackoffStateBetweenCalls() throws Exception {
        OkHttpClient client = createRetryingClient(1);

        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("pong"));
        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThat(execute(call).body().string()).isEqualTo("pong");

        // The following call would fail if OkHttpClients.create() constructed clients that share backoff state.
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("pong"));
        call = client.newCall(new Request.Builder().url(url).build());
        assertThat(execute(call).body().string()).isEqualTo("pong");

        assertThat(server.getRequestCount()).isEqualTo(4 /* two from each call */);
    }

    @Test
    public void interceptsAndHandlesQosIoExceptions_endToEnd_asyncCall() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("pong"));

        Call call = createRetryingClient(2).newCall(new Request.Builder().url(url).build());
        CompletableFuture<String> future = new CompletableFuture<>();
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException ioException) {
                future.completeExceptionally(ioException);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                future.complete(response.body().string());
            }
        });
        assertThat(future.get(500 * (2 + 4) /* ExponentialBackoff upper bound */, TimeUnit.MILLISECONDS))
                .isEqualTo("pong");
        assertThat(server.getRequestCount()).isEqualTo(3 /* original plus two retries */);
    }

    @Test
    public void interceptsAndHandlesRetryOther_endToEnd_doesRedirectInfinitelyOften() throws Exception {
        // QosRetryOtherInterceptor retries MAX=20 times
        for (int i = 0; i < 21; ++i) {
            server.enqueue(new MockResponse().setResponseCode(308).addHeader(HttpHeaders.LOCATION, url));
        }

        Call call = createRetryingClient(1).newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(() -> execute(call))
                .isInstanceOf(IOException.class)
                .hasMessage("Exceeded the maximum number of allowed redirects for initial URL: %s/", url);
        assertThat(server.getRequestCount()).isEqualTo(21);
    }

    @Test
    public void interceptsAndHandlesRetryOther_endToEnd_redirectsToOtherUrl() throws Exception {
        OkHttpClient client = OkHttpClients.withStableUris(
                ClientConfiguration.builder().from(createTestConfig(url, url2)).build(),
                AGENT, OkHttpClientsTest.class);
        server.enqueue(new MockResponse().setResponseCode(308).addHeader(HttpHeaders.LOCATION, url2));
        server2.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));

        Call call = client.newCall(new Request.Builder().url(url + "/foo?bar").build());
        assertThat(execute(call).body().string()).isEqualTo("foo");

        assertThat(server.takeRequest().getPath()).isEqualTo("/foo?bar");
        assertThat(server2.takeRequest().getPath()).isEqualTo("/foo?bar");
    }

    @Test
    public void interceptsAndHandlesQos_endToEnd_canRetryLaterAndThenRedirect() throws Exception {
        OkHttpClient client = OkHttpClients.withStableUris(
                ClientConfiguration.builder().from(createTestConfig(url, url2)).build(),
                AGENT, OkHttpClientsTest.class);
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(308).addHeader(HttpHeaders.LOCATION, url2));
        server2.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));

        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThat(execute(call).body().string()).isEqualTo("foo");

        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(server2.getRequestCount()).isEqualTo(1);
    }

    @Test
    public void interceptsAndHandlesQos_endToEnd_memorizedCurrentUrlBetweenCalls() throws Exception {
        OkHttpClient client = OkHttpClients.withStableUris(
                ClientConfiguration.builder().from(createTestConfig(url, url2)).build(),
                AGENT, OkHttpClientsTest.class);

        // First hits server,then 308 redirects to server2, then retries, waits on 503, then retries server2 again.
        server.enqueue(new MockResponse().setResponseCode(308).addHeader(HttpHeaders.LOCATION, url2));
        server2.enqueue(new MockResponse().setResponseCode(503));
        server2.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));

        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThat(execute(call).body().string()).isEqualTo("foo");

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(server2.getRequestCount()).isEqualTo(2);
    }

    private OkHttpClient createRetryingClient(int maxNumRetries) {
        return createRetryingClient(maxNumRetries, Duration.ofMillis(10));
    }

    private OkHttpClient createRetryingClient(int maxNumRetries, Duration backoffSlotSize) {
        return OkHttpClients.withStableUris(
                ClientConfiguration.builder().from(createTestConfig(url))
                        .maxNumRetries(maxNumRetries)
                        .backoffSlotSize(backoffSlotSize)
                        .build(),
                AGENT,
                OkHttpClientsTest.class);
    }

    private Response execute(Call call) throws Exception {
        return callExecutor.execute(call);
    }

    interface CallExecutor {

        CallExecutor BLOCKING = call -> call.execute();

        CallExecutor ASYNC = call -> {
            FutureCallback callback = new FutureCallback();
            call.enqueue(callback);
            try {
                return callback.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw (Exception) e.getCause();
            }
        };

        Response execute(Call call) throws Exception;
    }

    static class FutureCallback extends CompletableFuture<Response> implements Callback {

        @Override
        public void onFailure(Call call, IOException ex) {
            completeExceptionally(ex);
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            complete(response);
        }
    }
}
