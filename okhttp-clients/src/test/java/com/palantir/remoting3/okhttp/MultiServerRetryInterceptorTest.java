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

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class MultiServerRetryInterceptorTest {
    @Rule
    public final MockWebServer serverA = new MockWebServer();
    @Rule
    public final MockWebServer serverB = new MockWebServer();
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private HttpUrl urlA;
    private HttpUrl urlB;
    private OkHttpClient okHttpClient;

    @Before
    public void before() {
        urlA = serverA.url("/api/");
        urlB = serverB.url("/api/");
        okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(MultiServerRetryInterceptor.create(
                        ImmutableList.of(urlA.toString(), urlB.toString()), false))
                .build();
    }

    @Test
    public void testFailoverOn307() throws Exception {
        Request request = new Request.Builder()
                .url(urlA.newBuilder().addPathSegment("ping").build())
                .get()
                .build();

        serverA.enqueue(new MockResponse().setResponseCode(307));
        serverB.enqueue(new MockResponse().setBody("pong"));

        Call call = okHttpClient.newCall(request);

        assertThat(call.execute().body().string()).isEqualTo("pong");
    }

    @Test
    public void testDoNotHitSecondServerWhenFirstServerIsAvailable() throws IOException, InterruptedException {
        Request request = new Request.Builder()
                .url(urlA.newBuilder().addPathSegment("ping").build())
                .get()
                .build();

        serverA.enqueue(new MockResponse().setBody("pong"));

        Call call = okHttpClient.newCall(request);

        assertThat(call.execute().body().string()).isEqualTo("pong");

        RecordedRequest recordedRequest = serverA.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/api/ping");
    }

    @Test
    public void testRedirectToAvailableServerWhenFirstServerIsDown() throws IOException, InterruptedException {
        serverA.shutdown();

        Request request = new Request.Builder()
                .url(urlA.newBuilder().addPathSegment("ping").build())
                .get()
                .build();

        serverB.enqueue(new MockResponse().setBody("pong"));

        Call call = okHttpClient.newCall(request);

        assertThat(call.execute().body().string()).isEqualTo("pong");

        RecordedRequest recordedRequest = serverB.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/api/ping");
    }

    @Test
    public void testFailoverOnDnsError() throws IOException, InterruptedException {
        urlA = HttpUrl.parse("http://foo-bar-bogus-host.unresolvable");

        okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(MultiServerRetryInterceptor.create(
                        ImmutableList.of(urlA.toString(), urlB.toString()), false))
                .build();

        Request request = new Request.Builder()
                .url(urlA.newBuilder().addPathSegment("ping").build())
                .get()
                .build();

        serverB.enqueue(new MockResponse().setBody("pong"));
        Call call = okHttpClient.newCall(request);
        assertThat(call.execute().body().string()).isEqualTo("pong");

        RecordedRequest recordedRequest = serverB.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/api/ping");
    }

    @Test
    public void testThrowIoExceptionWhenNoServerIsAvailable() throws IOException {
        serverA.shutdown();
        serverB.shutdown();

        Request request = new Request.Builder()
                .url(urlA.newBuilder().addPathSegment("ping").build())
                .get()
                .build();

        expectedException.expect(IOException.class);
        okHttpClient.newCall(request).execute();
    }
}
