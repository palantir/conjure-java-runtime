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
    public void testDoNotHitSecondServerWhenFirstServerIsAvailable() throws IOException, InterruptedException {
        Request request = new Request.Builder()
                .url(urlA.newBuilder().addPathSegment("ping").build())
                .get()
                .build();

        serverA.enqueue(new MockResponse().setBody("pong"));

        Call call = okHttpClient.newCall(request);

        assertThat(call.execute().body().string(), is("pong"));

        RecordedRequest recordedRequest = serverA.takeRequest();
        assertThat(recordedRequest.getPath(), is("/api/ping"));
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

        assertThat(call.execute().body().string(), is("pong"));

        RecordedRequest recordedRequest = serverB.takeRequest();
        assertThat(recordedRequest.getPath(), is("/api/ping"));
    }

    @Test
    public void testThrowIllegalStateExceptionWhenNoServerIsAvailable() throws IOException {
        serverA.shutdown();
        serverB.shutdown();

        Request request = new Request.Builder()
                .url(urlA.newBuilder().addPathSegment("ping").build())
                .get()
                .build();

        expectedException.expect(IllegalStateException.class);
        okHttpClient.newCall(request).execute();
    }

    @Test
    public void testRetrofit2ClientWithMultiServerRetryInterceptorRedirectToAvailableServer() throws IOException {
        TestService service = Retrofit2Client.builder()
                .build(TestService.class, "agent", ImmutableList.of(urlA.toString(), urlB.toString()));

        serverA.shutdown();
        serverB.enqueue(new MockResponse().setBody("\"pong\""));

        assertThat(service.get().execute().body(), is("pong"));
    }
}
