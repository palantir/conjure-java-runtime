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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import retrofit2.Call;
import retrofit2.http.GET;

public final class UserAgentTest {

    private static final String USER_AGENT = "TestSuite/1 (0.0.0)";
    private static final UserAgentInterceptor userAgentInterceptor = UserAgentInterceptor.of(USER_AGENT);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Before
    public void before() {
        server.enqueue(new MockResponse().setBody("\"foo\""));
    }


    @Test
    public void testUserAgent_default() throws IOException {
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        Request request = new Request.Builder().url("http://url").build();
        Response responseSuccess = responseWithCode(request, 200);

        when(chain.request()).thenReturn(request);
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
    public void testUserAgent_defaultHeaderIsSent() throws InterruptedException, IOException {
        TestService service =
                Retrofit2Client.builder().build(TestService.class, USER_AGENT, "http://localhost:" + server.getPort());
        service.get().execute();

        RecordedRequest capturedRequest = server.takeRequest();
        assertThat(capturedRequest.getHeader("User-Agent"), is(USER_AGENT));
    }

    @Test
    public void testUserAgent_invalidUserAgentThrows() throws InterruptedException {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(is("User Agent must match pattern '[A-Za-z0-9()\\-#;/.,_\\s]+': !@"));
        Retrofit2Client.builder().build(TestService.class, "!@", "http://localhost:" + server.getPort());
    }

    private static Response responseWithCode(Request request, int code) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("unused")
                .build();
    }

    public interface TestService {
        @GET("/")
        Call<String> get();
    }
}
