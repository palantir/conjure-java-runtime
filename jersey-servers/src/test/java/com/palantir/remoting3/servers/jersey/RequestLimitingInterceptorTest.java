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

package com.palantir.remoting3.servers.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.ByteStreams;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class RequestLimitingInterceptorTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(RequestLimitingTestServer.class, "src/test/resources/test-server.yml");

    private WebTarget target;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
    }

    @Test
    public void testUnderLimit() {
        Response response = target.path("/limit").request().post(Entity.json("short"));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void testStringOverLimit() {
        Response response = target.path("/limit").request().post(Entity.json("over the limit"));
        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    public void testJsonOverLimit() {
        Response response = target.path("/limit-complex").request().post(Entity.json("{\"over\":true}"));
        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    public void testUnderLimit_streaming() {
        Response response = target.path("/streaming").request().post(Entity.json("short"));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void testOverLimit_streaming() {
        Response response = target.path("/streaming").request().post(Entity.json("over the limit"));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    public static class RequestLimitingTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) {
            env.jersey().register(new RequestLimitingInterceptor(10));
            env.jersey().register(new RequestLimitingTestResource());
            env.jersey().register(HttpRemotingJerseyFeature.INSTANCE);
        }
    }

    public static final class RequestLimitingTestResource implements RequestLimitingTestService {
        @Override
        public String limitedString(String string) {
            return string;
        }

        @Override
        public boolean limitedComplex(Map<String, Boolean> someComplexType) {
            return someComplexType.get("over");
        }

        @Override
        public String streaming(InputStream stream) {
            try {
                byte[] bytes = ByteStreams.toByteArray(stream);
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface RequestLimitingTestService {
        @POST
        @Path("/limit")
        String limitedString(String string);

        @POST
        @Path("/limit-complex")
        boolean limitedComplex(Map<String, Boolean> someComplexType);

        @POST
        @Path("/streaming")
        String streaming(InputStream stream);
    }
}
