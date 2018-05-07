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

package com.palantir.remoting3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.remoting.api.config.service.ServiceConfiguration;
import com.palantir.remoting.api.config.ssl.SslConfiguration;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting3.clients.ClientConfiguration;
import com.palantir.remoting3.clients.ClientConfigurations;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class OkHttpClientsTest extends TestBase {

    @Rule
    public final MockWebServer server = new MockWebServer();
    @Rule
    public final MockWebServer server2 = new MockWebServer();
    @Rule
    public final MockWebServer server3 = new MockWebServer();

    private String url;
    private String url2;
    private String url3;

    @Before
    public void before() {
        url = "http://localhost:" + server.getPort();
        url2 = "http://localhost:" + server2.getPort();
        url3 = "http://localhost:" + server3.getPort();
    }

    @Test
    public void verifyResponseMetricsAreRegistered() throws IOException {
        server.enqueue(new MockResponse().setBody("pong"));
        createRetryingClient(1).newCall(new Request.Builder().url(url).build()).execute();

        List<HostMetrics> hostMetrics = OkHttpClients.hostMetrics().stream()
                .filter(metrics -> metrics.hostname().equals("localhost"))
                .filter(metrics -> metrics.serviceName().equals("OkHttpClientsTest"))
                .filter(metrics -> metrics.url().equals(url + "/"))
                .collect(Collectors.toList());
        HostMetrics actualMetrics = Iterables.getOnlyElement(hostMetrics);

        assertThat(actualMetrics.get2xx().getCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void verifyIoExceptionMetricsAreRegistered() {
        Call call = createRetryingClient(0, "http://bogus").newCall(new Request.Builder().url("http://bogus").build());
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(call::execute);

        List<HostMetrics> hostMetrics = OkHttpClients.hostMetrics().stream()
                .filter(metrics -> metrics.hostname().equals("bogus"))
                .filter(metrics -> metrics.serviceName().equals("OkHttpClientsTest"))
                .collect(Collectors.toList());
        HostMetrics actualMetrics = Iterables.getOnlyElement(hostMetrics);

        assertThat(actualMetrics.getIoExceptions().getCount()).isEqualTo(1);
    }

    @Test
    public void handlesSuccessfulResponseCodesWithSuccessHandler() throws Exception {
        for (int code : new int[] {100, 101, 200, 204}) {
            server.enqueue(new MockResponse().setResponseCode(code));
            Call call = createRetryingClient(0).newCall(new Request.Builder().url(url).build());
            CountDownLatch wasSuccessful = new CountDownLatch(1);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException exception) {}

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.code() == code) {
                        wasSuccessful.countDown();
                    }
                }
            });
            assertThat(wasSuccessful.await(500, TimeUnit.MILLISECONDS)).as("Expected code: " + code).isTrue();
        }
    }

    @Test
    public void successfulCallDoesNotInvokeFailureHandler() throws Exception {
        server.enqueue(new MockResponse().setBody("pong"));

        Call call = createRetryingClient(0).newCall(new Request.Builder().url(url).build());
        Semaphore failureHandlerExecuted = new Semaphore(0);
        Semaphore successHandlerExecuted = new Semaphore(0);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException ioException) {
                failureHandlerExecuted.release();  // should never happen
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                successHandlerExecuted.release();
            }
        });
        assertThat(successHandlerExecuted.tryAcquire(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(failureHandlerExecuted.tryAcquire(500, TimeUnit.MILLISECONDS))
                .as("onFailure was executed").isFalse();
    }

    @Test
    public void unsuccessfulCallDoesNotInvokeSuccessHandler() throws Exception {
        server.shutdown();

        Call call = createRetryingClient(0).newCall(new Request.Builder().url(url).build());
        Semaphore failureHandlerExecuted = new Semaphore(0);
        Semaphore successHandlerExecuted = new Semaphore(0);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException ioException) {
                failureHandlerExecuted.release();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                successHandlerExecuted.release();  // should never happen
            }
        });
        assertThat(successHandlerExecuted.tryAcquire(100, TimeUnit.MILLISECONDS))
                .as("onSuccess was executed").isFalse();
        assertThat(failureHandlerExecuted.tryAcquire(100, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void doesNotHangIfManyCallsResultInExceptions() throws Exception {
        int maxRetries = OkHttpClients.NUM_SCHEDULING_THREADS * 2;

        for (int i = 0; i <= maxRetries; i++) {
            server.enqueue(new MockResponse().setResponseCode(503));
        }
        Call call = createRetryingClient(maxRetries, Duration.ofMillis(2) /* backoff slot size */)
                .newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute).isInstanceOf(IOException.class);
    }

    @Test
    public void throwsRemoteExceptionAfterRetry() throws Exception {
        // first we get a 503
        server.enqueue(new MockResponse().setResponseCode(503));

        // then we get a RemoteException
        SerializableError error = SerializableError.builder().errorCode("error code").errorName("error name").build();
        MockResponse mockResponse = new MockResponse()
                .setBody(new ObjectMapper().writeValueAsString(error))
                .addHeader("Content-Type", "application/json")
                .setResponseCode(400);
        server.enqueue(mockResponse);

        OkHttpClient client = createRetryingClient(1);
        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute).isInstanceOf(RemoteException.class);
    }

    @Test
    public void throwsIoExceptionWithCorrectBodyAfterFailingToDeserializeSerializableError() throws Exception {
        String responseJson = "{\"attribute\": \"foo\"}";
        MockResponse mockResponse = new MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json")
                .setResponseCode(400);
        server.enqueue(mockResponse);

        OkHttpClient client = createRetryingClient(1);
        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute).isInstanceOf(IOException.class).hasMessageContaining(responseJson);
    }

    @Test
    public void handlesUnavailable_obeysMaxNumRetriesAndEventuallyPropagatesQosException() throws Exception {
        Call call;

        server.enqueue(new MockResponse().setResponseCode(503));
        call = createRetryingClient(0).newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute)
                .isInstanceOf(IOException.class)
                .hasMessage("Failed to complete the request due to a server-side QoS condition: 503");

        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        call = createRetryingClient(2).newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute)
                .isInstanceOf(IOException.class)
                .hasMessage("Failed to complete the request due to a server-side QoS condition: 503");

        assertThat(server.getRequestCount()).isEqualTo(4 /* original plus two retries */);
    }

    @Test
    public void handlesUnavailable_succeedsWhenClientRetriesSufficientlyOften() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("pong"));

        Call call = createRetryingClient(2).newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("pong");
        assertThat(server.getRequestCount()).isEqualTo(3 /* original plus two retries */);
    }

    @Test
    public void handlesThrottle_obeysMaxNumRetriesAndEventuallyPropagatesQosException() throws Exception {
        Call call;

        server.enqueue(new MockResponse().setResponseCode(429));
        call = createRetryingClient(0).newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute)
                .isInstanceOf(IOException.class)
                .hasMessage("Failed to reschedule call since the number of configured backoffs are exhausted");

        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));
        call = createRetryingClient(2).newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute)
                .isInstanceOf(IOException.class)
                .hasMessage("Failed to reschedule call since the number of configured backoffs are exhausted");

        assertThat(server.getRequestCount()).isEqualTo(4 /* original plus two retries */);
    }

    @Test
    public void handlesThrottle_obeysMaxNumRetriesEvenWhenRetryAfterHeaderIsGiven() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429).addHeader(HttpHeaders.RETRY_AFTER, "0"));
        server.enqueue(new MockResponse().setResponseCode(429).addHeader(HttpHeaders.RETRY_AFTER, "0"));
        server.enqueue(new MockResponse().setResponseCode(429).addHeader(HttpHeaders.RETRY_AFTER, "0"));
        Call call = createRetryingClient(2).newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute)
                .isInstanceOf(IOException.class)
                .hasMessage("Failed to reschedule call since the number of configured backoffs are exhausted");
        assertThat(server.getRequestCount()).isEqualTo(3 /* original plus two retries */);
    }

    @Test
    public void handlesThrottle_succeedsWhenClientRetriesSufficientlyOften() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setResponseCode(429));
        server.enqueue(new MockResponse().setBody("pong"));

        Call call = createRetryingClient(2).newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("pong");
        assertThat(server.getRequestCount()).isEqualTo(3 /* original plus two retries */);
    }

    @Test
    public void handlesThrottle_usesConfiguredBackoffWhenResponseDoesNotAdvertiseBackoff() throws Exception {
        Call call;

        // backoff advertised, configured with no retry: uses advertised backoff
        server.enqueue(new MockResponse().setResponseCode(429).addHeader(HttpHeaders.RETRY_AFTER, "0"));
        server.enqueue(new MockResponse().setBody("foo"));
        call = createRetryingClient(1).newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("foo");

        // no backoff advertised, but configured with one retry: uses backoff to retry
        server.enqueue(new MockResponse().setResponseCode(429).setBody("foo"));
        server.enqueue(new MockResponse().setBody("foo"));
        call = createRetryingClient(1).newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("foo");

        // no backoff advertised, configured no retry: fails
        server.enqueue(new MockResponse().setResponseCode(429).setBody("foo"));
        call = createRetryingClient(0).newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute)
                .isInstanceOf(IOException.class)
                .hasMessage("Failed to reschedule call since the number of configured backoffs are exhausted");
    }

    @Test
    public void doesNotShareBackoffStateBetweenDifferentCalls() throws Exception {
        OkHttpClient client = createRetryingClient(1);

        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("pong"));
        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("pong");

        // The following call would fail if OkHttpClients.create() constructed clients that share backoff state.
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("pong"));
        call = client.newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("pong");

        assertThat(server.getRequestCount()).isEqualTo(4 /* two from each call */);
    }

    @Test
    public void handlesQosExceptions_asyncCall() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("pong"));

        Call call = createRetryingClient(2, Duration.ofMillis(10)).newCall(new Request.Builder().url(url).build());
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
        assertThat(future.get(100 * (2 + 4) /* generous exp backoff upper bound */, TimeUnit.MILLISECONDS))
                .isEqualTo("pong");
        assertThat(server.getRequestCount()).isEqualTo(3 /* original plus two retries */);
    }

    @Test
    public void handlesRetryOther_doesNotRedirectInfinitelyOften() throws Exception {
        // Note that RemotingOkHttpClient.MAX_NUM_RELOCATIONS = 20
        for (int i = 0; i < 21; ++i) {
            server.enqueue(new MockResponse().setResponseCode(308).addHeader(HttpHeaders.LOCATION, url));
        }

        Call call = createRetryingClient(1).newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute)
                .isInstanceOf(IOException.class)
                .hasMessage("Exceeded the maximum number of allowed redirects for initial URL: %s/", url);
        assertThat(server.getRequestCount()).isEqualTo(21);
    }

    @Test
    public void handlesRetryOther_redirectsToOtherUrl() throws Exception {
        OkHttpClient client = OkHttpClients.withStableUris(
                ClientConfiguration.builder().from(createTestConfig(url, url2)).build(),
                AGENT, OkHttpClientsTest.class);
        server.enqueue(new MockResponse().setResponseCode(308).addHeader(HttpHeaders.LOCATION, url2));
        server2.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));

        Call call = client.newCall(new Request.Builder().url(url + "/foo?bar").build());
        assertThat(call.execute().body().string()).isEqualTo("foo");

        assertThat(server.takeRequest().getPath()).isEqualTo("/foo?bar");
        assertThat(server2.takeRequest().getPath()).isEqualTo("/foo?bar");
    }

    @Test
    public void handlesQos_redirectsToOtherUrlThenRetriesAnotherUrl() throws Exception {
        // First hits server, then 308 redirects to server2, then 503 redirects back to server.
        server.enqueue(new MockResponse().setResponseCode(308).addHeader(HttpHeaders.LOCATION, url2));
        server2.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));

        OkHttpClient client = createRetryingClient(1, url, url2);
        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("foo");

        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(server2.getRequestCount()).isEqualTo(1);
    }

    @Test
    public void handlesQos_503FailsOverToAnotherUrl() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        server2.enqueue(new MockResponse().setBody("foo"));
        server2.enqueue(new MockResponse().setBody("bar"));

        OkHttpClient client = createRetryingClient(1, url, url2);
        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("foo");
        Call call2 = client.newCall(new Request.Builder().url(url).build());
        assertThat(call2.execute().body().string()).isEqualTo("bar");

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(server2.getRequestCount()).isEqualTo(2);
    }

    @Test
    public void handlesIoExceptions_retriesOtherServers() throws Exception {
        server.shutdown();
        server2.shutdown();
        server3.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));

        OkHttpClient client = createRetryingClient(2, url, url2, url3);
        Call call = client.newCall(new Request.Builder().url(url + "/foo?bar").build());
        assertThat(call.execute().body().string()).isEqualTo("foo");

        assertThat(server3.takeRequest().getPath()).isEqualTo("/foo?bar");
    }

    @Test
    public void handlesIoExceptions_obeysMaxNumRetries() throws Exception {
        server.shutdown();
        server2.shutdown();
        server3.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));

        OkHttpClient client = createRetryingClient(1, url, url2, url3);
        Call call = client.newCall(new Request.Builder().url(url + "/foo?bar").build());
        assertThatThrownBy(call::execute)
                .isInstanceOf(IOException.class)
                .hasMessage("Failed to complete the request due to an IOException");

        assertThat(server3.getRequestCount()).isEqualTo(0);
    }

    @Test(timeout = 10_000)
    public void handlesInterruptedThreads() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        OkHttpClient client = createRetryingClient(0);

        Thread thread = new Thread(() -> {
            try {
                client.newCall(new Request.Builder().url(url).build()).execute();
            } catch (IOException e) {
                // nothing
            }
        });

        thread.start();
        Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        thread.interrupt();
        thread.join();
    }

    @Test
    public void timeouts_set_to_all_zero_should_be_treated_as_infinity() throws Exception {
        server.enqueue(new MockResponse()
                .setBodyDelay(Duration.ofSeconds(11).toMillis(), TimeUnit.MILLISECONDS)
                .setBody("Hello, world!"));

        OkHttpClient client = OkHttpClients.withStableUris(
                ClientConfiguration.builder()
                        .from(createTestConfig(url))
                        .connectTimeout(Duration.ZERO)
                        .readTimeout(Duration.ZERO)
                        .writeTimeout(Duration.ZERO) // want to allow unlimited time for uploads
                        .maxNumRetries(0)
                        .backoffSlotSize(Duration.ofMillis(10))
                        .build(),
                AGENT,
                OkHttpClientsTest.class);

        Request request = new Request.Builder()
                .url(url)
                .build();

        Response synchronousCall = client.newCall(request).execute();
        assertThat(synchronousCall.body().string()).isEqualTo("Hello, world!");
    }

    @Test
    public void sync_call_to_a_slow_endpoint_should_not_time_out_if_read_timeout_is_zero() throws Exception {
        server.enqueue(new MockResponse()
                .setBodyDelay(Duration.ofSeconds(11).toMillis(), TimeUnit.MILLISECONDS)
                .setBody("Hello, world!"));

        OkHttpClient client = OkHttpClients.withStableUris(
                ClientConfigurations.of(
                        ServiceConfiguration.builder()
                                .addUris(url)
                                // ClientConfigurations has a connectTimeout default of 10 seconds
                                .readTimeout(Duration.ZERO) // unlimited pls
                                .writeTimeout(Duration.ZERO) // unlimited pls
                                .security(SslConfiguration.of(Paths.get("src", "test", "resources", "trustStore.jks")))
                                .build()
                ),
                AGENT,
                OkHttpClientsTest.class);

        Request request = new Request.Builder()
                .url(url)
                .build();

        Response synchronousCall = client.newCall(request).execute();
        assertThat(synchronousCall.body().string()).isEqualTo("Hello, world!");
    }

    @Test
    public void largestOf_sanity() throws Exception {
        assertThat(OkHttpClients.largestOf(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofHours(7)))
                .isEqualTo(Duration.ofHours(7));
    }

    @Test
    public void largestOf_treats_zero_as_infinity() throws Exception {
        assertThat(OkHttpClients.largestOf(Duration.ZERO, Duration.ofSeconds(20), Duration.ofHours(7)))
                .isEqualTo(Duration.ZERO);
    }

    @Test(timeout = 10_000)
    public void randomizesUrls() throws IOException {
        boolean server2WasHit = false;
        server.shutdown();
        server2.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));

        while (!server2WasHit) {
            OkHttpClient client = OkHttpClients.create(
                    ClientConfiguration.builder()
                            .from(createTestConfig(url, url2))
                            .maxNumRetries(0)
                            .build(),
                    AGENT,
                    OkHttpClientsTest.class);
            Call call = client.newCall(new Request.Builder().url(url).build());
            String response = null;
            try {
                response = call.execute().body().string();
            } catch (Exception e) {
                System.out.println("Failed to talk to shutdown server (this is expected with p=1/2)");
                assertThat(e.getCause().toString()).contains(Integer.toString(server.getPort()));
            }

            if (response != null) {
                assertThat(response).isEqualTo("foo");
                server2WasHit = true;
            }
        }
    }

    @Test
    public void meshProxyClientChangesTargetAndInjectHostHeader() throws Exception {
        server.enqueue(new MockResponse().setBody("foo"));

        String serviceUrl = "http://foo.com/";
        ClientConfiguration proxiedConfig = ClientConfiguration.builder()
                .from(createTestConfig(serviceUrl))
                .meshProxy(HostAndPort.fromParts("localhost", server.getPort()))
                .maxNumRetries(0)
                .build();
        OkHttpClient client = OkHttpClients.create(proxiedConfig, AGENT, OkHttpClientsTest.class);

        assertThat(client.newCall(new Request.Builder().url(serviceUrl).build()).execute().body().string())
                .isEqualTo("foo");
        assertThat(server.takeRequest().getHeader(HttpHeaders.HOST)).isEqualTo("foo.com");
    }

    private OkHttpClient createRetryingClient(int maxNumRetries) {
        return createRetryingClient(maxNumRetries, Duration.ofMillis(500));
    }

    private OkHttpClient createRetryingClient(int maxNumRetries, Duration backoffSlotSize) {
        return createRetryingClient(maxNumRetries, backoffSlotSize, url);
    }

    private OkHttpClient createRetryingClient(int maxNumRetries, String... urls) {
        return createRetryingClient(maxNumRetries, Duration.ofMillis(10), urls);
    }

    private OkHttpClient createRetryingClient(int maxNumRetries, Duration backoffSlotSize, String... urls) {
        return OkHttpClients.withStableUris(
                ClientConfiguration.builder()
                        .from(createTestConfig(urls))
                        .maxNumRetries(maxNumRetries)
                        .backoffSlotSize(backoffSlotSize)
                        .build(),
                AGENT,
                OkHttpClientsTest.class);
    }
}
