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

package com.palantir.remoting3.retrofit2;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting3.ext.jackson.ObjectMappers;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.ws.rs.core.MediaType;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class Retrofit2ClientApiTest extends TestBase {
    @Rule
    public final MockWebServer server = new MockWebServer();

    private HttpUrl url;
    private TestService service;

    @Before
    public void before() {
        url = server.url("/");
        service = Retrofit2Client.create(TestService.class, "agent", createTestConfig(url.toString()));
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
