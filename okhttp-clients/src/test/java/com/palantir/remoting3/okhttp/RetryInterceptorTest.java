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
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Test;

public final class RetryInterceptorTest {

    private RetryInterceptor retryInterceptor;
    private Interceptor.Chain chain;
    private Request request;
    private Response responseFail;
    private Response responseSuccess;

    @Before
    public void before() throws IOException {
        retryInterceptor = new RetryInterceptor();
        chain = mock(Interceptor.Chain.class);
        request = new Request.Builder().url("http://url").build();

        responseFail = responseWithCode(request, 500);
        responseSuccess = responseWithCode(request, 200);

        when(chain.request()).thenReturn(request);
    }

    @Test
    public void testNoRetries() throws IOException {
        when(chain.proceed(request)).thenReturn(responseSuccess);
        Response response = retryInterceptor.intercept(chain);

        assertThat(response).isEqualTo(responseSuccess);

        verify(chain).request();
        verify(chain).proceed(request);
        verifyNoMoreInteractions(chain);
    }

    @Test
    public void testRetriesSuccessful() throws IOException {
        when(chain.proceed(request))
                .thenReturn(responseFail)
                .thenReturn(responseFail)
                .thenReturn(responseSuccess);
        Response response = retryInterceptor.intercept(chain);

        assertThat(response).isEqualTo(responseSuccess);

        verify(chain).request();
        verify(chain, times(3)).proceed(request);
        verifyNoMoreInteractions(chain);
    }

    @Test
    public void testRetriesTooFew() throws IOException {
        when(chain.proceed(request))
                .thenReturn(responseFail)
                .thenReturn(responseFail)
                .thenReturn(responseFail)
                .thenReturn(responseSuccess);
        Response response = retryInterceptor.intercept(chain);

        assertThat(response).isEqualTo(responseFail);

        verify(chain).request();
        verify(chain, times(3)).proceed(request);
        verifyNoMoreInteractions(chain);
    }

    @Test
    public void testRetriesFailed() throws IOException {
        when(chain.proceed(request)).thenReturn(responseFail);
        Response response = retryInterceptor.intercept(chain);

        assertThat(response).isEqualTo(responseFail);

        verify(chain).request();
        verify(chain, times(3)).proceed(request);
        verifyNoMoreInteractions(chain);
    }

    @Test
    public void testRetriesFailed_exception() throws IOException {
        when(chain.proceed(request)).thenThrow(new IOException("connection error"));

        try {
            retryInterceptor.intercept(chain);
            fail();
        } catch (IOException e) {
            verify(chain).request();
            verify(chain, times(3)).proceed(request);
            verifyNoMoreInteractions(chain);
            assertThat(e.getMessage()).isEqualTo("connection error");
        }
    }

    private static Response responseWithCode(Request request, int code) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("unused")
                .build();
    }
}
