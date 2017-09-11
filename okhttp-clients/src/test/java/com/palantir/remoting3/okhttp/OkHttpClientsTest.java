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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Futures;
import com.palantir.remoting.api.errors.QosException;
import com.palantir.remoting3.clients.ClientConfiguration;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
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
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class OkHttpClientsTest extends TestBase {

    private static final Request REQUEST = new Request.Builder().url("http://127.0.0.1").build();

    @Rule
    public final MockWebServer server = new MockWebServer();
    @Rule
    public final MockWebServer server2 = new MockWebServer();

    @Mock
    private QosIoExceptionHandler handler;

    private String url;
    private String url2;
    private OkHttpClient mockHandlerClient;

    @Before
    public void before() {
        url = "http://localhost:" + server.getPort();
        url2 = "http://localhost:" + server2.getPort();
        mockHandlerClient = OkHttpClients.create(createTestConfig(url), "test", OkHttpClientsTest.class, () -> handler);
    }

    @Test
    public void interceptsAndHandlesQosIoExceptions_propagatesQosIoExceptions() throws Exception {
        QosIoException qosIoException = new QosIoException(QosException.unavailable(), responseWithCode(REQUEST, 503));
        when(handler.handle(any(), any())).thenReturn(Futures.immediateFailedFuture(qosIoException));
        server.enqueue(new MockResponse().setResponseCode(503));

        Call call = mockHandlerClient.newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute)
                .hasMessage("Failed to complete the request due to a server-side QoS condition: 503")
                .isInstanceOfSatisfying(QosIoException.class, actualException -> {
                    assertThat(actualException.getResponse()).isEqualTo(qosIoException.getResponse());
                    assertThat(actualException.getQosException()).isEqualTo(qosIoException.getQosException());
                });
        verify(handler).handle(any(), any());
    }

    @Test
    public void interceptsAndHandlesQosIoExceptions_wrapsRuntimeExceptionsAsIoExceptions() throws Exception {
        RuntimeException runtimeException = new RuntimeException("Foo");
        when(handler.handle(any(), any())).thenReturn(Futures.immediateFailedFuture(runtimeException));
        server.enqueue(new MockResponse().setResponseCode(503));

        Call call = mockHandlerClient.newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute)
                .hasMessage("Failed to execute request")
                .isInstanceOf(IOException.class)
                .hasCause(runtimeException);
        verify(handler).handle(any(), any());
    }

    @Test
    public void interceptsAndHandlesQosIoExceptions_endToEnd_whenClientRetriesSufficientlyOften() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("pong"));

        Call call = createRetryingClient(2).newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("pong");
        assertThat(server.getRequestCount()).isEqualTo(3 /* original plus two retries */);
    }

    @Test
    public void interceptsAndHandlesQosIoExceptions_endToEnd_whenClientDoesNotRetrySufficientlyOften()
            throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));

        Call call = createRetryingClient(1).newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute)
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
        assertThat(call.execute().body().string()).isEqualTo("pong");

        // The following call would fail if OkHttpClients.create() constructed clients that share backoff state.
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("pong"));
        call = client.newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("pong");

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
        assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo("pong");
        assertThat(server.getRequestCount()).isEqualTo(3 /* original plus two retries */);
    }

    @Test
    public void interceptsAndHandlesRetryOther_endToEnd_doesRedirectInfinitelyOften() throws Exception {
        // QosRetryOtherInterceptor retries MAX=20 times
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
    public void interceptsAndHandlesRetryOther_endToEnd_redirectsToOtherUrl() throws Exception {
        OkHttpClient client = OkHttpClients.create(
                ClientConfiguration.builder().from(createTestConfig(url, url2)).build(),
                "test", OkHttpClientsTest.class);
        server.enqueue(new MockResponse().setResponseCode(308).addHeader(HttpHeaders.LOCATION, url2));
        server2.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));

        Call call = client.newCall(new Request.Builder().url(url + "/foo?bar").build());
        assertThat(call.execute().body().string()).isEqualTo("foo");

        assertThat(server.takeRequest().getPath()).isEqualTo("/foo?bar");
        assertThat(server2.takeRequest().getPath()).isEqualTo("/foo?bar");
    }

    @Test
    public void interceptsAndHandlesQos_endToEnd_canRetryLaterAndThenRedirect() throws Exception {
        OkHttpClient client = OkHttpClients.create(
                ClientConfiguration.builder().from(createTestConfig(url, url2)).build(),
                "test", OkHttpClientsTest.class);
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(308).addHeader(HttpHeaders.LOCATION, url2));
        server2.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));

        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("foo");

        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(server2.getRequestCount()).isEqualTo(1);
    }

    @Test
    public void interceptsAndHandlesQos_endToEnd_memorizedCurrentUrlBetweenCalls() throws Exception {
        OkHttpClient client = OkHttpClients.create(
                ClientConfiguration.builder().from(createTestConfig(url, url2)).build(),
                "test", OkHttpClientsTest.class);

        // First hits server,then 308 redirects to server2, then retries, waits on 503, then retries server2 again.
        server.enqueue(new MockResponse().setResponseCode(308).addHeader(HttpHeaders.LOCATION, url2));
        server2.enqueue(new MockResponse().setResponseCode(503));
        server2.enqueue(new MockResponse().setResponseCode(200).setBody("foo"));

        Call call = client.newCall(new Request.Builder().url(url).build());
        assertThat(call.execute().body().string()).isEqualTo("foo");

        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(server2.getRequestCount()).isEqualTo(2);
    }

    private OkHttpClient createRetryingClient(int maxNumRetries) {
        return OkHttpClients.create(
                ClientConfiguration.builder().from(createTestConfig(url)).maxNumRetries(maxNumRetries).build(),
                "test",
                OkHttpClientsTest.class);
    }
}
