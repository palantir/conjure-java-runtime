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
import com.jcraft.jzlib.InflaterInputStream;
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

public final class DeflateFilterTest {

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
    public void testDeflate() throws NoSuchMethodException, SecurityException, IOException {
        Response response = baseRequest(target).header(HttpHeaders.ACCEPT_ENCODING, "deflate").get();
        byte[] entity = ByteStreams.toByteArray((InputStream) response.getEntity());

        assertThat(response.getHeaderString(HttpHeaders.CONTENT_ENCODING), is("deflate"));
        assertThat(toString(toStream(entity)), is(not("val")));
        assertThat(toString(new InflaterInputStream(toStream(entity))), is("val"));
    }

    @Test
    public void testNoDeflateHeader() throws NoSuchMethodException, SecurityException {
        Response response = baseRequest(target).get();

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

    public static class TestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(HttpRemotingJerseyFeature.DEFAULT);
            env.jersey().register(new DeflateFilter());
            env.jersey().register(new TestResource());
        }
    }

    public static final class TestResource implements OptionalTestService {
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
