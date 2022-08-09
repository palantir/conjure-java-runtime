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

import static org.assertj.core.api.Assertions.assertThat;

import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DropwizardExtensionsSupport.class)
public final class DateTimeTest {

    public static final DropwizardAppExtension<Configuration> APP =
            new DropwizardAppExtension<>(DateTimeTestServer.class, "src/test/resources/test-server.yml");

    private WebTarget target;

    @BeforeEach
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
    }

    @Test
    public void testOffsetDateTimeParam() throws SecurityException {
        Response response = target.path("offsetDateTime")
                .queryParam("value", "2017-01-02T03:04:05.06Z")
                .request()
                .get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("2017-01-02T03:04:05.060Z");
    }

    @Test
    public void testZonedDateTimeParam() throws SecurityException {
        Response response = target.path("zonedDateTime")
                .queryParam("value", "2017-01-02T03:04:05.06Z")
                .request()
                .get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("2017-01-02T03:04:05.060Z");
    }

    @Test
    public void testInstantParam() {
        Response response = target.path("instant")
                .queryParam("value", "2017-01-02T03:04:05.06Z")
                .request()
                .get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("2017-01-02T03:04:05.060Z");
    }

    public static class DateTimeTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration _config, final Environment env) {
            env.jersey().register(ConjureJerseyFeature.INSTANCE);
            env.jersey().register(new DateTimeTestResource());
        }
    }

    public static final class DateTimeTestResource implements DateTimeTestService {
        @Override
        public String getOffsetDateTime(OffsetDateTime value) {
            return value.toString();
        }

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
        @Path("/offsetDateTime")
        String getOffsetDateTime(@QueryParam("value") OffsetDateTime value);

        @GET
        @Path("/zonedDateTime")
        String getZonedDateTime(@QueryParam("value") ZonedDateTime value);

        @GET
        @Path("/instant")
        String getInstant(@QueryParam("value") Instant value);
    }
}
