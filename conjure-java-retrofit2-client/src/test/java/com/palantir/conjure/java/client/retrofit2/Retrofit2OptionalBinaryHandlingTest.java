/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.conjure.java.okhttp.NoOpHostEventsSink;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.GET;

public final class Retrofit2OptionalBinaryHandlingTest extends TestBase {
    private final MockWebServer server = new MockWebServer();

    private Service proxy;

    public static Stream<Arguments> responses() {
        Buffer nullValueBuffer = new Buffer();
        nullValueBuffer.write("null".getBytes(StandardCharsets.UTF_8));
        Buffer emptyBuffer = new Buffer();
        return Stream.of(
                Arguments.of(200, nullValueBuffer, false),
                Arguments.of(200, emptyBuffer, false),
                Arguments.of(204, emptyBuffer, true));
    }

    @BeforeEach
    public void before() throws IOException {
        server.start();
        HttpUrl url = server.url("/");
        proxy = Retrofit2Client.create(
                Service.class, AGENT, NoOpHostEventsSink.INSTANCE, createTestConfig(url.toString()));
    }

    @AfterEach
    void after() throws IOException {
        server.close();
    }

    public interface Service {
        @GET("/optional/binary")
        Call<Optional<ResponseBody>> getOptional();

        @GET("/optionalFuture/binary")
        CompletableFuture<Optional<ResponseBody>> getOptionalFuture();
    }

    @ParameterizedTest(name = "{index}: code {0} body: \"{1}\"")
    @MethodSource("responses")
    public void testOptional(int code, Buffer body, boolean empty) throws IOException {
        server.enqueue(new MockResponse().setResponseCode(code).setBody(body));
        assertCallBody(proxy.getOptional(), optional -> {
            if (empty) {
                assertThat(optional).isEmpty();
            } else {
                assertThat(optional).isPresent();
            }
        });
    }

    @ParameterizedTest(name = "{index}: code {0} body: \"{1}\"")
    @MethodSource("responses")
    public void testOptionalFuture(int code, Buffer body, boolean empty) throws Exception {
        server.enqueue(new MockResponse().setResponseCode(code).setBody(body));
        assertFuture(proxy.getOptionalFuture(), optional -> {
            if (empty) {
                assertThat(optional).isEmpty();
            } else {
                assertThat(optional).isPresent();
            }
        });
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
