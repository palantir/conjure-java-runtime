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

package com.palantir.remoting2.servers.jersey;


import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import com.jcraft.jzlib.GZIPInputStream;
import com.jcraft.jzlib.InflaterInputStream;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class CompressionFilterTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(TestServer.class, "src/test/resources/test-server.yml");

    private WebTarget target;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
    }

    @Test
    public void testDeflate() throws IOException {
        Response response = baseRequest(target).acceptEncoding("deflate").get();

        assertThat(response.getHeaderString(HttpHeaders.VARY), is(HttpHeaders.ACCEPT_ENCODING));
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), is("deflate"));
        assertThat(toString(new InflaterInputStream(toStream(response))), is("val"));
    }

    @Test
    public void testGzip() throws IOException {
        Response response = baseRequest(target).acceptEncoding("gzip").get();

        assertThat(response.getHeaderString(HttpHeaders.VARY), is(HttpHeaders.ACCEPT_ENCODING));
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), is("gzip"));
        assertThat(toString(new GZIPInputStream(toStream(response))), is("val"));
    }

    @Test
    public void testPreferGzipOverDeflate() throws IOException {
        Response response = baseRequest(target).acceptEncoding("deflate", "gzip").get();

        assertThat(response.getHeaderString(HttpHeaders.VARY), is(HttpHeaders.ACCEPT_ENCODING));
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), is("gzip"));
        assertThat(toString(new GZIPInputStream(toStream(response))), is("val"));
    }

    // TODO(jellis): minimum size does nothing because Content-Length header is not set
    @Test
    public void testResponseTooSmall() throws IOException {
        Response response = baseRequest(target, "a").acceptEncoding("gzip").get();

        assertThat(response.getHeaderString(HttpHeaders.VARY), is(HttpHeaders.ACCEPT_ENCODING));
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), is("gzip"));
        assertThat(toString(new GZIPInputStream(toStream(response))), is("a"));
    }

    // TODO(jellis): support qvalues
    @Test
    public void testIgnoresQvalues() throws IOException {
        Response response = baseRequest(target).acceptEncoding("gzip;q=0.8").get();

        assertThat(response.getHeaderString(HttpHeaders.VARY), is(HttpHeaders.ACCEPT_ENCODING));
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), is("gzip"));
        assertThat(toString(new GZIPInputStream(toStream(response))), is("val"));
    }

    @Test
    public void testHandleWhitespace() throws IOException {
        Response response = baseRequest(target).acceptEncoding("deflate, gzip").get();

        assertThat(response.getHeaderString(HttpHeaders.VARY), is(HttpHeaders.ACCEPT_ENCODING));
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), is("gzip"));
        assertThat(toString(new GZIPInputStream(toStream(response))), is("val"));
    }

    @Test
    public void testNoCompressionWithoutHeader() {
        Response response = baseRequest(target).get();

        assertThat(response.getHeaderString(HttpHeaders.VARY), is(nullValue()));
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), is(nullValue()));
        assertThat(response.readEntity(String.class), is("val"));
    }

    @Test
    public void testNoCompressionForUncompressableResponse() {
        Response response = target.path("uncompressible").request().get();

        assertThat(response.getHeaderString(HttpHeaders.VARY), is(nullValue()));
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), is(nullValue()));
        assertThat(response.readEntity(String.class), is("value"));
    }

    private static Invocation.Builder baseRequest(WebTarget target) {
        return baseRequest(target, "val");
    }

    private static Invocation.Builder baseRequest(WebTarget target, String response) {
        return target.path("path").queryParam("value", response).request();
    }

    private static String toString(InputStream is) {
        try {
            return new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static InputStream toStream(Response response) {
        return (InputStream) response.getEntity();
    }

    public static class TestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(HttpRemotingJerseyFeature.INSTANCE);
            env.jersey().register(new CompressionFilter(3));
            env.jersey().register(new TestResource());
        }
    }

    public static final class TestResource implements OptionalTestService {
        @Override
        public String get(@Nullable String value) {
            return value;
        }

        @Override
        public String getUncompressible() {
            return "value";
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface OptionalTestService {
        @GET
        @Path("/path")
        String get(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/uncompressible")
        @Produces("audio/audio")
        String getUncompressible();

    }

}
