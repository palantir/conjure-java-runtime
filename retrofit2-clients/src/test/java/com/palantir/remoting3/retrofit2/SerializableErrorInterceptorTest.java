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

package com.palantir.remoting3.retrofit2;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting3.ext.jackson.ObjectMappers;
import com.palantir.remoting3.okhttp.AsyncCallTag;
import com.palantir.remoting3.okhttp.SerializableErrorInterceptor;
import java.io.IOException;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.http.RealResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class SerializableErrorInterceptorTest extends TestBase {

    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();

    private final AsyncCallTag tag = new AsyncCallTag();
    private final Request request = new Request.Builder().url("http://url").tag(tag).build();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public final MockWebServer server = new MockWebServer();

    private TestService service;
    @Mock
    private Interceptor.Chain chain;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        when(chain.request()).thenReturn(request);
        service = Retrofit2Client.create(TestService.class, AGENT,
                createTestConfig("http://localhost:" + server.getPort()));
    }

    @Test
    public void doesNothingIfAsyncCall() throws IOException {
        tag.setCallAsync();
        Response response = responseWithCode(request, 400);
        when(chain.proceed(any(Request.class))).thenReturn(response);
        assertThat(SerializableErrorInterceptor.INSTANCE.intercept(chain), Matchers.is(response));
    }

    @Test
    public void testThrowsIfHttpCodeIsNot2xx() throws Exception {
        for (int code : ImmutableList.of(300, 400, 404, 500)) {
            Response response = responseWithCode(request, code);
            when(chain.proceed(any(Request.class))).thenReturn(response);

            try {
                SerializableErrorInterceptor.INSTANCE.intercept(chain);
                fail();
            } catch (RuntimeException e) {
                assertThat(e.getMessage(), Matchers.containsString("Error " + code));
            }
        }
    }

    @Test
    public void testInterceptorLogic_isIdentityOperationIfHttpCodeIs200() throws Exception {
        Response response = responseWithCode(request, 200);
        when(chain.proceed(any(Request.class))).thenReturn(response);

        assertThat(SerializableErrorInterceptor.INSTANCE.intercept(chain), Matchers.is(response));
    }

    @Test
    public void testDeserialization_withContentTypeTextPlain() throws InterruptedException, IOException {
        MockResponse mockResponse = new MockResponse()
                .setBody("errorbody")
                .addHeader("Content-Type", "text/html")
                .setResponseCode(400);
        server.enqueue(mockResponse);

        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Error 400. Body:\nerrorbody");
        service.get().execute();
    }

    @Test
    public void testDeserialization_withContentTypeJson() throws InterruptedException, IOException {
        SerializableError error = SerializableError.builder().errorCode("error code").errorName("error name").build();
        MockResponse mockResponse = new MockResponse()
                .setBody(MAPPER.writeValueAsString(error))
                .addHeader("Content-Type", "application/json")
                .setResponseCode(400);
        server.enqueue(mockResponse);

        expectedException.expect(RemoteException.class);
        expectedException.expectMessage("error name");
        service.get().execute();
    }

    @Test
    public void testInterceptorLogic_isIdentityOperationIfHttpCodeIs101SwitchingProtocols() throws Exception {
        Response response = responseWithCode(request, 101);
        when(chain.proceed(any(Request.class))).thenReturn(response);

        assertThat(SerializableErrorInterceptor.INSTANCE.intercept(chain), Matchers.is(response));
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
