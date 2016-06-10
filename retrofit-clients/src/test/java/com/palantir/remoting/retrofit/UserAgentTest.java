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

package com.palantir.remoting.retrofit;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import java.io.IOException;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import retrofit.http.GET;

public final class UserAgentTest {

    private static final String USER_AGENT = "TestSuite/1 (0.0.0)";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public final MockWebServer server = new MockWebServer();

    private TestService service;
    private UserAgentInterceptor userAgentInterceptor;
    private Interceptor.Chain chain;
    private Request request;
    private Response responseSuccess;

    @Before
    public void before() {
        userAgentInterceptor = UserAgentInterceptor.of(USER_AGENT);
        chain = mock(Interceptor.Chain.class);
        request = new Request.Builder().url("http://url").build();

        responseSuccess = responseWithCode(request, 200);

        when(chain.request()).thenReturn(request);

        String endpointUri = "http://localhost:" + server.getPort();

        service = RetrofitClientFactory.createProxy(
                Optional.<SSLSocketFactory>absent(),
                endpointUri,
                TestService.class,
                OkHttpClientOptions.builder().build(),
                USER_AGENT
                );

        server.enqueue(new MockResponse().setBody("{}"));
    }


    @Test
    public void  testUserAgent_default() throws IOException {
        when(chain.proceed(Matchers.any(Request.class))).thenReturn(responseSuccess);
        Response response = userAgentInterceptor.intercept(chain);

        assertThat(response, is(responseSuccess));

        verify(chain).request();
        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        verify(chain).proceed(argument.capture());
        assertThat(argument.getValue().header("User-Agent"), is(USER_AGENT));
        verifyNoMoreInteractions(chain);
    }

    @Test
    public void  testUserAgent_defaultHeaderIsSent() throws InterruptedException {
        service.get();

        RecordedRequest capturedRequest = server.takeRequest();
        assertThat(capturedRequest.getHeader("User-Agent"), is(USER_AGENT));
    }


    @Test
    public void testUserAgent_invalidUserAgentThrows() throws InterruptedException {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(is("User Agent must match pattern '[A-Za-z0-9()/\\.,_\\s]+': !@"));

        OkHttpClientOptions okHttpClientOptions = OkHttpClientOptions.builder().build();

        RetrofitClientFactory.createProxy(Optional.<SSLSocketFactory>absent(), "", String.class, okHttpClientOptions,
                "!@");
    }

    private static Response responseWithCode(Request request, int code) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .build();
    }

    public interface TestService {
        @GET("/")
        Response get();
    }

}
