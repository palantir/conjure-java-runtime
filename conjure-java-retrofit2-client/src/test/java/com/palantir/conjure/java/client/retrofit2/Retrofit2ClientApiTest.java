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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.google.common.net.HttpHeaders;
import com.palantir.logsafe.exceptions.SafeNullPointerException;
import com.palantir.logsafe.testing.Assertions;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.serialization.ObjectMappers;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.MediaType;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Response;

public final class Retrofit2ClientApiTest extends TestBase {
    @Rule
    public final MockWebServer server = new MockWebServer();

    private static final SerializableError ERROR = SerializableError.builder()
            .errorCode("errorCode")
            .errorName("errorName")
            .build();

    private HttpUrl url;
    private TestService service;

    @Before
    public void before() {
        url = server.url("/");
        service = Retrofit2Client.create(TestService.class, AGENT, createTestConfig(url.toString()));
    }

    @Test
    public void testOptionalStringHandling() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getGuavaOptionalString(guavaOptional("p"), guavaOptional("q")).execute().body())
                .isEqualTo(guavaOptional("pong"));
        assertThat(server.takeRequest().getPath())
                .isEqualTo("/getGuavaOptionalString/p/?queryString=q");

        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getJava8OptionalString(java8Optional("p"), java8Optional("q")).execute().body())
                .isEqualTo(java8Optional("pong"));
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalString/p/?queryString=q");

        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getGuavaOptionalString(guavaOptional("p"), guavaEmptyOptional()).execute().body())
                .isEqualTo(guavaOptional("pong"));
        assertThat(server.takeRequest().getPath())
                .isEqualTo("/getGuavaOptionalString/p/");

        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getGuavaOptionalString(guavaEmptyOptional(), guavaEmptyOptional()).execute().body())
                .isEqualTo(guavaOptional("pong"));
        assertThat(server.takeRequest().getPath())
                .isEqualTo("/getGuavaOptionalString//");
    }

    @Test
    public void testOptionalComplexReturnValues() throws IOException, InterruptedException {
        LocalDate date = LocalDate.of(2001, 2, 3);
        String dateString = "\"2001-02-03\"";

        server.enqueue(new MockResponse().setBody(dateString));
        assertThat(service.getComplexGuavaType(guavaOptional(date)).execute().body()).isEqualTo(guavaOptional(date));
        assertThat(server.takeRequest().getBody().readUtf8()).isEqualTo(dateString);

        server.enqueue(new MockResponse().setBody(dateString));
        assertThat(service.getComplexJava8Type(java8Optional(date)).execute().body()).isEqualTo(java8Optional(date));
        assertThat(server.takeRequest().getBody().readUtf8()).isEqualTo(dateString);
    }

    @Test
    public void should_reject_body_containing_json_null() {
        server.enqueue(new MockResponse().setBody("null"));
        Assertions.assertThatLoggableExceptionThrownBy(() -> service.getRelative().execute().body())
                .hasMessage("Unexpected null body")
                .isInstanceOf(SafeNullPointerException.class);
    }

    @Test
    public void should_reject_body_containing_empty_string() {
        server.enqueue(new MockResponse().setBody(""));
        assertThatThrownBy(() -> service.getRelative().execute().body())
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    public void testCborReturnValues() throws IOException {
        LocalDate date = LocalDate.of(2001, 2, 3);
        byte[] bytes = ObjectMappers.newCborServerObjectMapper().writeValueAsBytes(Optional.of(date));
        try (Buffer buffer = new Buffer()) {
            buffer.write(bytes);
            server.enqueue(new MockResponse().setBody(buffer).addHeader("Content-Type", "application/cbor"));
            assertThat(service.getComplexCborType().execute().body()).isEqualTo(java8Optional(date));
        }
    }

    @Test
    public void testCborRequests() throws IOException, InterruptedException {
        LocalDate date = LocalDate.of(2001, 2, 3);

        server.enqueue(new MockResponse());
        service.makeCborRequest(date).execute();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getBody().readByteArray())
                .isEqualTo(ObjectMappers.newCborClientObjectMapper().writeValueAsBytes(date));
    }

    @Test
    public void makeFutureRequest() {
        String value = "value";
        server.enqueue(new MockResponse().setBody("\"" + value + "\""));
        CompletableFuture<String> future = service.makeFutureRequest();
        assertThat(future.join()).isEqualTo("value");
    }

    @Test
    public void makeFutureRequestError() throws JsonProcessingException {
        SerializableError error = SerializableError.builder()
                .errorCode("errorCode")
                .errorName("errorName")
                .build();

        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBody(ObjectMappers.newClientObjectMapper().writeValueAsString(error)));

        CompletableFuture<String> future = service.makeFutureRequest();

        try {
            future.join();
            fail();
        } catch (CompletionException e) {
            assertThat(e.getCause()).isInstanceOf(RemoteException.class);
            assertThat(((RemoteException) e.getCause()).getError()).isEqualTo(error);
        }
    }

    @Test
    public void connectionFailureWithCompletableFuture() {
        service = Retrofit2Client.create(TestService.class, AGENT,
                ClientConfiguration.builder()
                        .from(createTestConfig("https://invalid.service.dev"))
                        .connectTimeout(Duration.ofMillis(10))
                        .build());

        assertThatExceptionOfType(CompletionException.class).isThrownBy(() -> service.makeFutureRequest().join());
    }

    @Test
    public void completableFuture_should_throw_RemoteException_for_server_serializable_errors() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBody(ObjectMappers.newClientObjectMapper().writeValueAsString(ERROR)));

        CompletableFuture<String> future = service.makeFutureRequest();

        try {
            future.join();
            failBecauseExceptionWasNotThrown(CompletionException.class);
        } catch (CompletionException e) {
            assertThat(e.getCause()).isInstanceOf(RemoteException.class);
            RemoteException remoteException = (RemoteException) e.getCause();
            assertThat(remoteException.getError()).isEqualTo(ERROR);
        }
    }

    @Test
    public void sync_retrofit_call_should_throw_RemoteException_for_server_serializable_errors() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBody(ObjectMappers.newClientObjectMapper().writeValueAsString(ERROR)));

        Call<String> call = service.getRelative();

        try {
            call.execute();
            failBecauseExceptionWasNotThrown(RemoteException.class);
        } catch (RemoteException e) {
            assertThat(e.getError()).isEqualTo(ERROR);
        }
    }

    @Ignore("TODO(rfink): Async Retrofit calls should produce RemoteException, Issue #625")
    @Test
    public void async_retrofit_call_should_throw_RemoteException_for_server_serializable_errors() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBody(ObjectMappers.newClientObjectMapper().writeValueAsString(ERROR)));

        CountDownLatch assertionsPassed = new CountDownLatch(1);
        retrofit2.Call<String> call = service.getRelative();
        call.enqueue(new retrofit2.Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                failBecauseExceptionWasNotThrown(RemoteException.class);
            }

            @Override
            public void onFailure(Call<String> call, Throwable throwable) {
                assertThat(throwable).isInstanceOf(RemoteException.class);
                assertThat(((RemoteException) throwable).getError()).isEqualTo(ERROR);
                assertionsPassed.countDown(); // if you delete this countdown latch then this test will vacuously pass.
            }
        });
        assertThat(assertionsPassed.await(1, TimeUnit.SECONDS)).as("Callback was executed").isTrue();
    }

    @Test
    public void completableFuture_should_throw_normal_IoException_for_client_side_errors() {
        service = Retrofit2Client.create(TestService.class, AGENT,
                ClientConfiguration.builder()
                        .from(createTestConfig("https://invalid.service.dev"))
                        .connectTimeout(Duration.ofMillis(10))
                        .build());

        CompletableFuture<String> completableFuture = service.makeFutureRequest();

        try {
            completableFuture.join();
        } catch (CompletionException e) {
            assertThat(e.getCause()).isInstanceOf(IOException.class);
            assertThat(e.getCause().getMessage()).isEqualTo("Failed to complete the request due to an IOException");
        }
    }

    private static <T> com.google.common.base.Optional<T> guavaOptional(T value) {
        return com.google.common.base.Optional.of(value);
    }

    private static <T> com.google.common.base.Optional<T> guavaEmptyOptional() {
        return com.google.common.base.Optional.absent();
    }

    private static <T> java.util.Optional<T> java8Optional(T value) {
        return java.util.Optional.of(value);
    }
}
