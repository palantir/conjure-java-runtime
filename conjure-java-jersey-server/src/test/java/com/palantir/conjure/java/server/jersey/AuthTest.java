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

import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.tokens.auth.AuthHeader;
import com.palantir.tokens.auth.BearerToken;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class AuthTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(
            AuthTestServer.class,
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
    public void testAuthHeader() throws SecurityException {
        Response response = target.path("authHeader")
                .request()
                .header("value", "Bearer bearerToken")
                .get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("Bearer bearerToken");
    }

    @Test
    public void testAuthHeader_missingCredentials() throws SecurityException {
        Response response = target.path("authHeader")
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(Status.UNAUTHORIZED.getStatusCode());

        SerializableError error = response.readEntity(SerializableError.class);
        assertThat(error.errorCode()).isEqualTo(ErrorType.UNAUTHORIZED.code().toString());
        assertThat(error.errorName()).isEqualTo("Conjure:MissingCredentials");
        assertThat(error.parameters()).isEmpty();
    }

    @Test
    public void testAuthHeader_malformedCredentials() throws SecurityException {
        Response response = target.path("authHeader")
                .request()
                .header("value", "")
                .get();

        assertThat(response.getStatus()).isEqualTo(Status.UNAUTHORIZED.getStatusCode());

        SerializableError error = response.readEntity(SerializableError.class);
        assertThat(error.errorCode()).isEqualTo(ErrorType.UNAUTHORIZED.code().toString());
        assertThat(error.errorName()).isEqualTo("Conjure:MalformedCredentials");
        assertThat(error.parameters()).isEmpty();
    }

    @Test
    public void testBearerToken() throws SecurityException {
        Response response = target.path("bearerToken")
                .request()
                .header("value", "bearerToken")
                .get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("bearerToken");
    }

    @Test
    public void testBearerToken_missingCredentials() throws SecurityException {
        Response response = target.path("bearerToken")
                .request()
                .get();

        assertThat(response.getStatus()).isEqualTo(Status.UNAUTHORIZED.getStatusCode());

        SerializableError error = response.readEntity(SerializableError.class);
        assertThat(error.errorCode()).isEqualTo(ErrorType.UNAUTHORIZED.code().toString());
        assertThat(error.errorName()).isEqualTo("Conjure:MissingCredentials");
        assertThat(error.parameters()).isEmpty();
    }

    @Test
    public void testBearerToken_malformedCredentials() throws SecurityException {
        Response response = target.path("bearerToken")
                .request()
                .header("value", "")
                .get();

        assertThat(response.getStatus()).isEqualTo(Status.UNAUTHORIZED.getStatusCode());

        SerializableError error = response.readEntity(SerializableError.class);
        assertThat(error.errorCode()).isEqualTo(ErrorType.UNAUTHORIZED.code().toString());
        assertThat(error.errorName()).isEqualTo("Conjure:MalformedCredentials");
        assertThat(error.parameters()).isEmpty();
    }

    public static class AuthTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration _config, final Environment env) {
            env.jersey().register(ConjureJerseyFeature.INSTANCE);
            env.jersey().register(new AuthTestResource());
        }
    }

    public static final class AuthTestResource implements AuthTestService {
        @Override
        public String getAuthHeader(AuthHeader value) {
            return value.toString();
        }

        @Override
        public String getBearerToken(BearerToken value) {
            return value.toString();
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface AuthTestService {
        @GET
        @Path("/authHeader")
        String getAuthHeader(@HeaderParam("value") AuthHeader value);

        @GET
        @Path("/bearerToken")
        String getBearerToken(@HeaderParam("value") BearerToken value);
    }
}
