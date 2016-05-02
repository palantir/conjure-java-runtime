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

package com.palantir.remoting.http.server;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.palantir.tracing.TraceState;
import com.palantir.tracing.Traces;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.util.UUID;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class TraceInheritingFilterTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(TracingTestServer.class,
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
    public void testTraceState_withHeaderUsesTraceId() {
        Response response = target.path("/trace").request().header(Traces.TRACE_HEADER, "abc").get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
        assertThat(response.readEntity(TraceState.class),
                is((TraceState) TraceState.builder().traceId("abc").operation("GET /trace").build()));
    }

    @Test
    public void testTraceState_respectsMethod() {
        Response response = target.path("/trace").request().header(Traces.TRACE_HEADER, "abc").post(Entity.json(""));
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
        assertThat(response.readEntity(TraceState.class),
                is((TraceState) TraceState.builder().traceId("abc").operation("POST /trace").build()));
    }

    @Test
    public void testTraceState_withoutHeaderGeneratesValidUuid() {
        Response response = target.path("/trace").request().get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
        TraceState state = response.readEntity(TraceState.class);
        assertThat(UUID.fromString(state.getTraceId()).toString(), is(state.getTraceId()));
    }

    @Test
    public void testTraceState_withEmptyHeaderGeneratesValidUuid() {
        Response response = target.path("/trace").request().header(Traces.TRACE_HEADER, "").get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
        TraceState state = response.readEntity(TraceState.class);
        assertThat(UUID.fromString(state.getTraceId()).toString(), is(state.getTraceId()));
    }

    public static class TracingTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(new TraceInheritingFilter());
            env.jersey().register(new TracingTestResource());
        }
    }

    public static final class TracingTestResource implements TracingTestService {
        @Override
        public TraceState getTrace() {
            return Traces.getTrace();
        }

        @Override
        public TraceState postTrace() {
            return Traces.getTrace();
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TracingTestService {
        @GET
        @Path("/trace")
        TraceState getTrace();

        @POST
        @Path("/trace")
        TraceState postTrace();
    }

}
