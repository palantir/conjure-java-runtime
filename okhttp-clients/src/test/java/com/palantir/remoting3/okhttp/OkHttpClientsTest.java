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

import com.google.common.util.concurrent.Futures;
import com.palantir.remoting.api.errors.QosException;
import com.palantir.remoting3.clients.ClientConfiguration;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Mock
    private QosIoExceptionHandler handler;

    private String url;
    private OkHttpClient mockHandlerClient;

    @Before
    public void before() {
        url = "http://localhost:" + server.getPort();
        mockHandlerClient = OkHttpClients.create(createTestConfig(url), "test", OkHttpClientsTest.class, () -> handler);
    }

    @Test
    public void interceptsAndHandlesQosIoExceptions_propagatesQosIoExceptions() throws Exception {
        QosIoException qosIoException = new QosIoException(QosException.unavailable());
        when(handler.handle(any(), any())).thenReturn(Futures.immediateFailedFuture(qosIoException));
        server.enqueue(new MockResponse().setResponseCode(503));

        Call call = mockHandlerClient.newCall(new Request.Builder().url(url).build());
        assertThatThrownBy(call::execute)
                .hasMessage("Failed to complete the request due to a server-side QoS condition")
                .isEqualTo(qosIoException);
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
                .hasMessage("Failed to complete the request due to a server-side QoS condition")
                .isInstanceOf(QosIoException.class);
        assertThat(server.getRequestCount()).isEqualTo(2 /* original plus one retries */);
    }

    private OkHttpClient createRetryingClient(int maxNumRetries) {
        return OkHttpClients.create(
                ClientConfiguration.builder().from(createTestConfig(url)).maxNumRetries(maxNumRetries).build(),
                "test",
                OkHttpClientsTest.class);
    }
}
