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

package com.palantir.remoting3.servers.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.net.HttpHeaders;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public final class DeprecationWarningFilterTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(TestServer.class, "src/test/resources/test-server.yml");

    private WebTarget target;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
    }

    @Test
    public void addsHeaderForDeprecatedEndpoints() {
        Response response = target.path("/deprecated/unsafe-arg").request().get();
        assertThat(response.getHeaderString(HttpHeaders.WARNING))
                .isEqualTo("299 - \"Service API endpoint is deprecated: /deprecated/{arg}\"");

        response = target.path("/deprecated-in-resource").request().get();
        assertThat(response.getHeaderString(HttpHeaders.WARNING))
                .isEqualTo("299 - \"Service API endpoint is deprecated: /deprecated-in-resource\"");
    }

    @Test
    public void doesNotAddHeaderForNonDeprecatedEndpoints() {
        Response response = target.path("/not-deprecated").request().get();
        assertThat(response.getHeaderString(HttpHeaders.WARNING)).isNullOrEmpty();
    }

    public static class TestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(DeprecationWarningFilter.INSTANCE);
            env.jersey().register(new TestResource());
        }
    }

    public static final class TestResource implements TestService {
        @Override
        public void deprecated(String arg) {}

        /**
         * @deprecated foo
         */
        @Override
        @Deprecated
        public void deprecatedInResource() {}

        @Override
        public void notDeprecated() {}
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TestService {
        /**
         * @deprecated foo
         */
        @GET
        @Path("/deprecated/{arg}")
        @Deprecated
        void deprecated(String arg);

        @GET
        @Path("/deprecated-in-resource")
        void deprecatedInResource();

        @GET
        @Path("/not-deprecated")
        void notDeprecated();
    }
}
