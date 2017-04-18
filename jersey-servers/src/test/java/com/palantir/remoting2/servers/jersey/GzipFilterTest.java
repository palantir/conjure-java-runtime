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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.net.HttpHeaders;
import com.jcraft.jzlib.GZIPInputStream;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

public final class GzipFilterTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> GZIP_APP =
            new DropwizardAppRule<>(GzipTestServer.class, "src/test/resources/test-server.yml");

    private WebTarget gzip_target;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + GZIP_APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        gzip_target = client.target(endpointUri);
    }

    @Test
    public void testGzip() throws NoSuchMethodException, SecurityException, IOException {
        Response response = baseRequest(gzip_target).header(HttpHeaders.ACCEPT_ENCODING, "gzip").get();
        byte[] entity = ByteStreams.toByteArray((InputStream) response.getEntity());

        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), is("gzip"));
        assertThat(toString(toStream(entity)), is(not("val")));
        assertThat(toString(new GZIPInputStream(toStream(entity))), is("val"));
    }

    @Test
    public void testNoGzipHeader() throws NoSuchMethodException, SecurityException {
        Response response = baseRequest(gzip_target).get();

        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), is(nullValue()));
        assertThat(response.readEntity(String.class), is("val"));
    }

    private static Invocation.Builder baseRequest(WebTarget target) {
        return target.path("path").queryParam("value", "val").request();
    }

    private static String toString(InputStream is) throws IOException {
        return CharStreams.toString(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    private static InputStream toStream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    public static class GzipTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(HttpRemotingJerseyFeature.DEFAULT);
            env.jersey().register(new GzipFilter());
            env.jersey().register(new GzipTestResource());
        }
    }

    public static final class GzipTestResource implements OptionalTestService {
        @Override
        public String get(@Nullable String value) {
            return value;
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface OptionalTestService {
        @GET
        @Path("/path")
        String get(@QueryParam("value") @Nullable String value);
    }

}
