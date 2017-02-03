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
import static org.junit.Assert.assertThat;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.util.OptionalInt;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class Java8OptionalIntTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(OptionalTestServer.class,
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
    public void testOptionalPresent() throws NoSuchMethodException, SecurityException {
        Response response = target.path("optionalint").queryParam("value", "10").request().get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
        assertThat(response.readEntity(Integer.class), is(10));
    }

    @Test
    public void testOptionalAbsent() {
        Response response = target.path("optionalint").request().get();
        assertThat(response.getStatus(), is(Status.NO_CONTENT.getStatusCode()));
    }

    public static class OptionalTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(HttpRemotingJerseyFeature.DEFAULT);
            env.jersey().register(new OptionalIntTestResource());
        }
    }

    public static final class OptionalIntTestResource implements OptionalIntTestService {

        @Override
        public OptionalInt getOptionalInt(OptionalInt value) {
            return value;
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface OptionalIntTestService {
        @GET
        @Path("/optionalint")
        OptionalInt getOptionalInt(@QueryParam("value") OptionalInt value);
    }
}
