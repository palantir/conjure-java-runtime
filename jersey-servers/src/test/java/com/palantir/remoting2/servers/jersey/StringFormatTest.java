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

package com.palantir.remoting2.servers.jersey;


import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class StringFormatTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(TestServer.class,
            "src/test/resources/test-server.yml");

    private WebTarget target;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
    }

    @Test
    public void testTextPlainMediaTypeReturnsPlainStrings() {
        Response response = target.path("textString").request().get();
        assertThat(response.readEntity(String.class), is(""));
        response = target.path("textString").queryParam("value", "val").request().get();
        assertThat(response.readEntity(String.class), is("val"));
    }

    @Test
    public void testJsonMediaTypeReturnsPlainStrings() {
        // This behaviour is somewhat unexpected since a valid JSON response object would be "\"val\"" rather than "val"
        Response response = target.path("jsonString").request().get();
        assertThat(response.readEntity(String.class), is(""));
        response = target.path("jsonString").queryParam("value", "val").request().get();
        assertThat(response.readEntity(String.class), is("val"));
    }

    public static class TestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(HttpRemotingJerseyFeature.INSTANCE);
            env.jersey().register(new TestResource());
        }
    }

    public static final class TestResource implements TestService {
        @Override
        public String getJsonString(@Nullable String value) {
            return value;
        }

        @Override
        public String getTextString(@Nullable String value) {
            return value;
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public interface TestService {

        @GET
        @Path("/jsonString")
        @Produces(MediaType.APPLICATION_JSON)
        String getJsonString(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/textString")
        @Produces(MediaType.TEXT_PLAIN)
        String getTextString(@QueryParam("value") @Nullable String value);
    }
}
