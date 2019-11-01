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

package com.palantir.conjure.java.client.retrofit2;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.GET;

@RunWith(Parameterized.class)
public final class Retrofit2ClientCollectionHandlingTest extends TestBase {

    @Rule public final MockWebServer server = new MockWebServer();

    private HttpUrl url;
    private Service proxy;

    @Parameters(name = "{index}: code {0} body: \"{1}\"")
    public static Collection<Object[]> responses() {
        return Arrays.asList(new Object[][] {
            {200, "null"},
            {200, ""},
            {204, ""}
        });
    }

    @Parameter public int code;

    @Parameter(1)
    public String body;

    @Before
    public void before() {
        url = server.url("/");
        proxy = Retrofit2Client.create(
                Service.class, AGENT, new HostMetricsRegistry(), createTestConfig(url.toString()));
        MockResponse mockResponse = new MockResponse().setResponseCode(code).setBody(body);
        server.enqueue(mockResponse);
    }

    public interface Service {
        @GET("/list")
        Call<List<String>> getList();

        @GET("/set")
        Call<Set<String>> getSet();

        @GET("/map")
        Call<Map<String, String>> getMap();

        @GET("/listFuture")
        CompletableFuture<List<String>> getListFuture();

        @GET("/setFuture")
        CompletableFuture<Set<String>> getSetFuture();

        @GET("/mapFuture")
        CompletableFuture<Map<String, String>> getMapFuture();
    }

    @Test
    public void testList() throws IOException {
        assertCallBody(proxy.getList(), list -> assertThat(list).isEmpty());
    }

    @Test
    public void testSet() throws IOException {
        assertCallBody(proxy.getSet(), set -> assertThat(set).isEmpty());
    }

    @Test
    public void testMap() throws IOException {
        assertCallBody(proxy.getMap(), map -> assertThat(map).isEmpty());
    }

    @Test
    public void testListFuture() throws Exception {
        assertFuture(proxy.getListFuture(), list -> assertThat(list).isEmpty());
    }

    @Test
    public void testSetFuture() throws Exception {
        assertFuture(proxy.getSetFuture(), set -> assertThat(set).isEmpty());
    }

    @Test
    public void testMapFuture() throws Exception {
        assertFuture(proxy.getMapFuture(), map -> assertThat(map).isEmpty());
    }

    private static <T> void assertCallBody(Call<T> call, Consumer<T> assertions) throws IOException {
        Response<T> response = call.execute();
        assertThat(response.isSuccessful()).isTrue();
        assertions.accept(response.body());
    }

    private static <T> void assertFuture(CompletableFuture<T> future, Consumer<T> assertions) throws Exception {
        T value = future.get(1, TimeUnit.SECONDS);
        assertions.accept(value);
    }
}
