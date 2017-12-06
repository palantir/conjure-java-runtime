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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Optional;
import okhttp3.Call;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class MultiServerRetryInterceptorTest extends TestBase {

    @Rule
    public final MockWebServer serverA = new MockWebServer();
    @Rule
    public final MockWebServer serverB = new MockWebServer();
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Mock
    private UrlSelector urlSelector;
    @Mock
    private Dns dns;
    @Mock
    private Interceptor.Chain chain;

    private HttpUrl urlA;
    private HttpUrl urlB;
    private MultiServerRetryInterceptor interceptor;
    private OkHttpClient okHttpClient;
    private Request request;

    @Before
    public void before() throws UnknownHostException {
        urlA = HttpUrl.parse("http://host-a:" + serverA.getPort() + "/api/");
        urlB = HttpUrl.parse("http://host-b:" + serverB.getPort() + "/api/");
        request = new Request.Builder().url(urlA).build();
        interceptor = MultiServerRetryInterceptor.create(urlSelector, 2);
        okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .dns(dns)
                .build();

        when(dns.lookup(anyString())).thenReturn(ImmutableList.of(InetAddresses.forString("127.0.0.1")));
        when(chain.request()).thenReturn(request);

    }

    @Test
    public void interceptorTriesOriginalRequestBeforeCheckingForTheNextUrl() throws Exception {
        Response response = responseWithCode(request, 200);
        when(chain.proceed(request)).thenReturn(response);
        assertThat(interceptor.intercept(chain)).isEqualTo(response);
        verifyNoMoreInteractions(urlSelector);
    }

    @Test
    public void interceptorCatchesIoExceptionsAndRetriesUpToMaxTimes() throws Exception {
        when(chain.proceed(any())).thenThrow(
                new IOException("0"),
                new IOException("1"),
                new IOException("2"));
        when(urlSelector.redirectToNext(urlA)).thenReturn(Optional.of(urlB));
        when(urlSelector.redirectToNext(urlB)).thenReturn(Optional.of(urlA));

        assertThatThrownBy(() -> interceptor.intercept(chain))
                .isInstanceOf(IOException.class)
                .hasMessageStartingWith("Could not connect to any of the configured URLs:")
                .hasCause(new IOException("2")); // eventually wraps and throws exception from last retry
        verify(urlSelector).redirectToNext(urlA);
        verify(urlSelector).redirectToNext(urlB);
    }

    @Test
    public void doesNotRetryWhenMaxNumRetriesIsZero() throws Exception {
        when(chain.proceed(any())).thenThrow(new IOException("0"));
        interceptor = MultiServerRetryInterceptor.create(urlSelector, 0);
        assertThatThrownBy(() -> interceptor.intercept(chain))
                .isInstanceOf(IOException.class)
                .hasMessageStartingWith("Could not connect to any of the configured URLs:")
                .hasCause(new IOException("0")); // eventually wraps and throws exception from last retry
        verify(urlSelector, never()).redirectToNext(any());
    }

    @Test
    public void testClient_usesRequestServerFirstByDefault() throws IOException, InterruptedException {
        serverA.enqueue(new MockResponse().setBody("pong"));

        Call call = okHttpClient.newCall(request);
        assertThat(call.execute().body().string()).isEqualTo("pong");
        verify(urlSelector, never()).redirectToNext(any());
        assertThat(serverA.getRequestCount()).isEqualTo(1);
        assertThat(serverB.getRequestCount()).isEqualTo(0);
    }

    @Test
    public void testClient_performsFailoverOnDnsError() throws IOException, InterruptedException {
        when(dns.lookup(urlA.host())).thenThrow(new UnknownHostException("Cannot resolve url"));

        serverB.enqueue(new MockResponse().setBody("pong"));
        when(urlSelector.redirectToNext(any())).thenReturn(Optional.of(urlB));
        Call call = okHttpClient.newCall(request);
        assertThat(call.execute().body().string()).isEqualTo("pong");

        assertThat(serverA.getRequestCount()).isEqualTo(0);
        assertThat(serverB.getRequestCount()).isEqualTo(1);
    }
}
