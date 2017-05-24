/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.retrofit2;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.remoting2.errors.RemoteException;
import com.palantir.remoting2.errors.SerializableError;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import java.io.IOException;
import javax.xml.ws.WebServiceException;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.http.RealResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class SerializableErrorInterceptorTest {

    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();
    private static final Request REQUEST = new Request.Builder().url("http://url").build();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Mock private AsyncCallTracker asyncCallTracker;

    private TestService service;
    @Mock
    private Interceptor.Chain chain;

    private SerializableErrorInterceptor interceptor;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        when(chain.request()).thenReturn(REQUEST);
        when(asyncCallTracker.isAsyncRequest(any())).thenReturn(false);
        service = Retrofit2Client.builder().build(TestService.class, "agent", "http://localhost:" + server.getPort());
        interceptor = new SerializableErrorInterceptor(asyncCallTracker);
    }

    @Test
    public void doesNothingIfAsyncCall() throws IOException {
        when(asyncCallTracker.isAsyncRequest(any())).thenReturn(true);
        Response response = responseWithCode(REQUEST, 400);
        when(chain.proceed(any(Request.class))).thenReturn(response);
        assertThat(interceptor.intercept(chain), is(response));
    }

    @Test
    public void testThrowsIfHttpCodeIsNot2xx() throws Exception {
        for (int code : ImmutableList.of(300, 400, 404, 500)) {
            Response response = responseWithCode(REQUEST, code);
            when(chain.proceed(any(Request.class))).thenReturn(response);

            try {
                interceptor.intercept(chain);
                fail();
            } catch (RuntimeException e) {
                assertThat(e.getMessage(), containsString("Error " + code));
            }
        }
    }

    @Test
    public void testInterceptorLogic_isIdentityOperationIfHttpCodeIs200() throws Exception {
        Response response = responseWithCode(REQUEST, 200);
        when(chain.proceed(any(Request.class))).thenReturn(response);

        assertThat(interceptor.intercept(chain), is(response));
    }

    @Test
    public void testDeserialization_withContentTypeTextPlain() throws InterruptedException, IOException {
        MockResponse mockResponse = new MockResponse()
                .setBody("errorbody")
                .addHeader("Content-Type", "text/html")
                .setResponseCode(400);
        server.enqueue(mockResponse);

        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Error 400. Reason: Client Error. Body:\nerrorbody");
        service.get().execute();
    }

    @Test
    public void testDeserialization_withContentTypeJson() throws InterruptedException, IOException {
        SerializableError error = SerializableError.of("error message", WebServiceException.class);
        MockResponse mockResponse = new MockResponse()
                .setBody(MAPPER.writeValueAsString(error))
                .addHeader("Content-Type", "application/json")
                .setResponseCode(400);
        server.enqueue(mockResponse);

        expectedException.expect(RemoteException.class);
        expectedException.expectMessage("error message");
        service.get().execute();
    }

    private static Response responseWithCode(Request request, int code) {
        return new Response.Builder()
                .body(new RealResponseBody(Headers.of(), new Buffer()))
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("unused")
                .build();
    }
}
