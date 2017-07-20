/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting2.servers.jersey;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.palantir.tokens.auth.AuthHeader;
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
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class AuthHeaderTest {

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
    public void testInvalidAuthHeader() {
        Response response = target.path("authHeader")
                    .request()
                    .header("Authorization", "$INVALID")
                    .get();
        assertThat(response.getStatus(), is(Status.UNAUTHORIZED.getStatusCode()));
        assertThat(response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE), is("Bearer"));
    }

    @Test
    public void testValidAuthHeader() {
        Response response = target.path("authHeader")
                .request()
                .header("Authorization", "VALID")
                .get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
    }

    public static class OptionalTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(HttpRemotingJerseyFeature.DEFAULT);
            env.jersey().register(new AuthHeaderTestResource());
        }
    }

    public static final class AuthHeaderTestResource implements AuthHeaderTestService {
        @Override
        public String getAuthHeader(AuthHeader value) {
            return value.toString();
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface AuthHeaderTestService {
        @GET
        @Path("/authHeader")
        String getAuthHeader(@HeaderParam("Authorization") AuthHeader value);
    }
}
