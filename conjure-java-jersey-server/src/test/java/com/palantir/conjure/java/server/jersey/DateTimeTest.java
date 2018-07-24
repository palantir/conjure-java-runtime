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

package com.palantir.conjure.java.server.jersey;


import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.time.Instant;
import java.time.ZonedDateTime;
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

public final class DateTimeTest {

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
    public void testZonedDateTimeParam() throws NoSuchMethodException, SecurityException {
        Response response = target.path("zonedDateTime").queryParam("value", "2017-01-02T03:04:05.06Z").request().get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
        assertThat(response.readEntity(String.class), is("2017-01-02T03:04:05.060Z"));
    }

    @Test
    public void testInstantParam() {
        Response response = target.path("instant").queryParam("value", "2017-01-02T03:04:05.06Z").request().get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
        assertThat(response.readEntity(String.class), is("2017-01-02T03:04:05.060Z"));
    }

    public static class OptionalTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(ConjureJerseyFeature.INSTANCE);
            env.jersey().register(new DateTimeTestResource());
        }
    }

    public static final class DateTimeTestResource implements DateTimeTestService {
        @Override
        public String getZonedDateTime(ZonedDateTime value) {
            return value.toString();
        }

        @Override
        public String getInstant(Instant value) {
            return value.toString();
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface DateTimeTestService {
        @GET
        @Path("/zonedDateTime")
        String getZonedDateTime(@QueryParam("value") ZonedDateTime value);

        @GET
        @Path("/instant")
        String getInstant(@QueryParam("value") Instant value);
    }
}
