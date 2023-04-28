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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.GET;

public final class Retrofit2ClientCollectionHandlingTest extends TestBase {

    private final MockWebServer server = new MockWebServer();

    private HttpUrl url;
    private Service proxy;

    public static Stream<Arguments> responses() {
        return Stream.of(Arguments.of(200, "null"), Arguments.of(200, ""), Arguments.of(204, ""));
    }

    @BeforeEach
    public void before() throws IOException {
        server.start();
        url = server.url("/");
        proxy = Retrofit2Client.create(
                Service.class, AGENT, new HostMetricsRegistry(), createTestConfig(url.toString()));
    }

    @AfterEach
    void after() throws IOException {
        server.close();
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

    private void enqueueResponse(int code, String body) {
        server.enqueue(new MockResponse().setResponseCode(code).setBody(body));
    }

    @ParameterizedTest(name = "{index}: code {0} body: \"{1}\"")
    @MethodSource("responses")
    public void testList(int code, String body) throws IOException {
        enqueueResponse(code, body);
        assertCallBody(proxy.getList(), list -> assertThat(list).isEmpty());
    }

    @ParameterizedTest(name = "{index}: code {0} body: \"{1}\"")
    @MethodSource("responses")
    public void testSet(int code, String body) throws IOException {
        enqueueResponse(code, body);
        assertCallBody(proxy.getSet(), set -> assertThat(set).isEmpty());
    }

    @ParameterizedTest(name = "{index}: code {0} body: \"{1}\"")
    @MethodSource("responses")
    public void testMap(int code, String body) throws IOException {
        enqueueResponse(code, body);
        assertCallBody(proxy.getMap(), map -> assertThat(map).isEmpty());
    }

    @ParameterizedTest(name = "{index}: code {0} body: \"{1}\"")
    @MethodSource("responses")
    public void testListFuture(int code, String body) throws Exception {
        enqueueResponse(code, body);
        assertFuture(proxy.getListFuture(), list -> assertThat(list).isEmpty());
    }

    @ParameterizedTest(name = "{index}: code {0} body: \"{1}\"")
    @MethodSource("responses")
    public void testSetFuture(int code, String body) throws Exception {
        enqueueResponse(code, body);
        assertFuture(proxy.getSetFuture(), set -> assertThat(set).isEmpty());
    }

    @ParameterizedTest(name = "{index}: code {0} body: \"{1}\"")
    @MethodSource("responses")
    public void testMapFuture(int code, String body) throws Exception {
        enqueueResponse(code, body);
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
