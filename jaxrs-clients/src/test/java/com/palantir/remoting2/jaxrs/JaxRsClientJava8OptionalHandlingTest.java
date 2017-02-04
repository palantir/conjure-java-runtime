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

package com.palantir.remoting2.jaxrs;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Optional;
import java.util.OptionalInt;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class JaxRsClientJava8OptionalHandlingTest {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private FakeoInterface proxy;

    @Before
    public void before() {
        proxy = JaxRsClient.builder()
                .build(FakeoInterface.class, "agent", "http://localhost:" + server.getPort());
        server.enqueue(new MockResponse().setBody("\"foo\""));
    }

    @Path("/")
    public interface CannotDecorateInterfaceOptional {
        @GET
        @Path("{opt}/foo")
        String path(@PathParam("opt") Optional<String> opt);
    }

    @Path("/")
    public interface CannotDecorateInterfaceOptionalIntPath {
        @GET
        @Path("{opt}/foo")
        String path(@PathParam("opt") OptionalInt opt);
    }

    @Path("/")
    public interface CannotDecorateInterfaceOptionalIntHeader {
        @GET
        @Path("{opt}/foo")
        String path(@PathParam("opt") OptionalInt opt);
    }

    @Path("/")
    public interface FakeoInterface {
        @GET
        @Path("foo")
        String query(@QueryParam("opt") Optional<String> opt);

        @GET
        @Path("foo")
        String header(@HeaderParam("opt") Optional<String> opt);

        @GET
        @Path("foo")
        String queryInt(@QueryParam("opt") OptionalInt opt);
    }

    @Test
    public void testCannotDecorateInterfaceWithOptionalPathParam() {
        assertFailsToDecorateInterface(CannotDecorateInterfaceOptional.class);
    }

    @Test
    public void testCannotDecorateInterfaceWithOptionalIntPathParam() {
        assertFailsToDecorateInterface(CannotDecorateInterfaceOptionalIntPath.class);
    }

    @Test
    public void testCannotDecorateInterfaceWithOptionalIntHeaderParam() {
        assertFailsToDecorateInterface(CannotDecorateInterfaceOptionalIntHeader.class);
    }

    private void assertFailsToDecorateInterface(Class<?> clazz) {
        try {
            JaxRsClient.builder().build(clazz, "agent", "http://localhost:" + server.getPort());
            fail();
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is(String.format("Cannot use Java8 optional type with PathParams. (Class: %s, Method: path, Param: arg0)", clazz.getName())));
        }
    }

    @Test
    public void testOptionalAbsentQuery() throws Exception {
        proxy.query(Optional.empty());
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getPath(), is("/foo"));
    }

    @Test
    public void testOptionalEmptyStringQuery() throws Exception {
        proxy.query(Optional.of(""));
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getPath(), is("/foo?opt="));
    }

    @Test
    public void testOptionalStringQuery() throws Exception {
        proxy.query(Optional.of("str"));
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getPath(), is("/foo?opt=str"));
    }

    @Test
    public void testOptionalAbsentHeader() throws Exception {
        proxy.header(Optional.empty());
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt"), is(""));
    }

    @Test
    public void testOptionalEmptyStringHeader() throws Exception {
        proxy.header(Optional.of(""));
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt"), is(""));
    }

    @Test
    public void testOptionalStringHeader() throws Exception {
        proxy.header(Optional.of("str"));
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt"), is("str"));
    }

    @Test
    public void testOptionalIntAbsentPathParam() throws InterruptedException {
        proxy.queryInt(OptionalInt.empty());
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getPath(), is("/foo"));
    }

    @Test
    public void testOptionalIntPathParam() throws InterruptedException {
        proxy.queryInt(OptionalInt.of(4));
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getPath(), is("/foo?opt=4"));
    }

}
