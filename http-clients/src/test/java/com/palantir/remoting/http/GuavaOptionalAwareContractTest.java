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

package com.palantir.remoting.http;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class GuavaOptionalAwareContractTest {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private FakeoInterface proxy;

    @Before
    public void before() {
        proxy = FeignClients.standard().createProxy(Optional.<SSLSocketFactory>absent(),
                ImmutableSet.of("http://localhost:" + server.getPort()),
                FakeoInterface.class);
        server.enqueue(new MockResponse().setBody("\"foo\""));
    }

    @Path("/")
    public interface FakeoInterface {
        @GET
        @Path("{opt}/foo/{req}")
        String path(@PathParam("opt") Optional<String> opt, @PathParam("req") String req);

        @GET
        @Path("foo")
        String query(@QueryParam("opt") Optional<String> opt, @QueryParam("req") String req);

        @GET
        @Path("foo")
        String header(@HeaderParam("opt") Optional<String> opt, @HeaderParam("req") String req);
    }

    @Test
    public void testAbsentPath() throws Exception {
        proxy.path(Optional.<String>absent(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        // note that this differs from feign's default behavior of leaving the template
        // in the path. The default would be "/{opt}/foo"
        // This is because feign expands null values to their parameter name if they
        // have no Expander and the value is null
        assertThat(takeRequest.getPath(), is("/null/foo/str2"));
    }

    @Test
    public void testEmptyStringPath() throws Exception {
        proxy.path(Optional.<String>of(""), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getPath(), is("//foo/str2"));
    }

    @Test
    public void testStringPath() throws Exception {
        proxy.path(Optional.<String>of("str"), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getPath(), is("/str/foo/str2"));
    }

    @Test
    public void testAbsentQuery() throws Exception {
        proxy.query(Optional.<String>absent(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine(), is("GET /foo?req=str2 HTTP/1.1"));
    }

    @Test
    public void testEmptyStringQuery() throws Exception {
        proxy.query(Optional.<String>of(""), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine(), is("GET /foo?opt=&req=str2 HTTP/1.1"));
    }

    @Test
    public void testStringQuery() throws Exception {
        proxy.query(Optional.<String>of("str"), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine(), is("GET /foo?opt=str&req=str2 HTTP/1.1"));
    }

    @Test
    public void testAbsentHeader() throws Exception {
        proxy.header(Optional.<String>absent(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt"), is("{opt}"));
        assertThat(takeRequest.getHeader("req"), is("str2"));
    }

    @Test
    public void testEmptyStringHeader() throws Exception {
        proxy.header(Optional.<String>of(""), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt"), is(""));
        assertThat(takeRequest.getHeader("req"), is("str2"));
    }

    @Test
    public void testStringHeader() throws Exception {
        proxy.header(Optional.<String>of("str"), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt"), is("str"));
        assertThat(takeRequest.getHeader("req"), is("str2"));
    }

}
