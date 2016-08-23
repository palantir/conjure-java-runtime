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

package com.palantir.remoting1.retrofit2;


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
import org.junit.Rule;
import org.junit.Test;

public final class MultiServerRetryInterceptorTest {
    @Rule
    public final MockWebServer unavailableServer = new MockWebServer();
    @Rule
    public final MockWebServer availableServer = new MockWebServer();

    @Test
    public void interceptsRequestAndRedirectsToAvailableServer() throws IOException, InterruptedException {
        unavailableServer.shutdown();

        HttpUrl unavailable = unavailableServer.url("/api");
        HttpUrl available = availableServer.url("/api");

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(MultiServerRetryInterceptor.create(
                        ImmutableList.of(unavailable.toString(), available.toString()), false))
                .build();

        Request request = new Request.Builder()
                .url(unavailable.newBuilder().addPathSegment("ping").build())
                .get()
                .build();

        availableServer.enqueue(new MockResponse().setBody("pong"));

        Call call = okHttpClient.newCall(request);

        assertThat(call.execute().body().string(), is("pong"));

        RecordedRequest recordedRequest = availableServer.takeRequest();
        assertThat(recordedRequest.getPath(), is("/api/ping"));
    }
}
