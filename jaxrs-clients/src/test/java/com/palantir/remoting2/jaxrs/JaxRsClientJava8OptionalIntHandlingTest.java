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

public final class JaxRsClientJava8OptionalIntHandlingTest {

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
    public interface CannotDecorateInterfacePathParams {
        @GET
        @Path("{opt}/foo")
        String path(@PathParam("opt") OptionalInt opt);
    }

    @Path("/")
    public interface CannotDecorateInterfaceHeaderParams {
        @GET
        @Path("foo")
        String header(@HeaderParam("opt") OptionalInt opt);
    }

    @Path("/")
    public interface FakeoInterface {
        @GET
        @Path("foo")
        String query(@QueryParam("opt") OptionalInt opt);
    }

    @Test
    public void testCannotDecorateInterfaceWithOptionalIntPathParam() {
        try {
            JaxRsClient.builder().build(CannotDecorateInterfacePathParams.class, "agent", "http://localhost:" + server.getPort());
            fail();
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is(String.format(
                    "Cannot use Java8 OptionalInt with PathParams. (Class: %s, Method: path, Param: arg0)",
                    CannotDecorateInterfacePathParams.class.getName()
            )));
        }
    }

    @Test
    public void testCannotDecorateInterfaceWithOptionalIntHeaderParam() {
        try {
            JaxRsClient.builder().build(CannotDecorateInterfaceHeaderParams.class, "agent", "http://localhost:" + server.getPort());
            fail();
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is(String.format(
                    "Cannot use Java8 OptionalInt with HeaderParams. (Class: %s, Method: header, Param: arg0)",
                    CannotDecorateInterfaceHeaderParams.class.getName()
            )));
        }
    }

    @Test
    public void testAbsentQuery() throws Exception {
        proxy.query(OptionalInt.empty());
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine(), is("GET /foo HTTP/1.1"));
    }

    @Test
    public void testPresentQuery() throws Exception {
        proxy.query(OptionalInt.of(4));
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine(), is("GET /foo?opt=4 HTTP/1.1"));
    }
}
