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

package com.palantir.remoting1.servers.jersey;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.palantir.remoting1.tracing.Traces;
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
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class TracingTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(TracingTestServer.class, "src/test/resources/test-server.yml");

    private WebTarget target;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
    }

    @Test
    public void testTracingFilterIsApplied() {
        Response response = target.path("/trace").request()
                .header(Traces.HttpHeaders.TRACE_ID, "traceId")
                .header(Traces.HttpHeaders.PARENT_SPAN_ID, "parentSpanId")
                .header(Traces.HttpHeaders.SPAN_ID, "spanId")
                .get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
        assertThat(response.readEntity(String.class), is("GET /trace"));
        assertThat(response.getHeaderString(Traces.HttpHeaders.TRACE_ID), is("traceId"));
        assertThat(response.getHeaderString(Traces.HttpHeaders.PARENT_SPAN_ID), is("parentSpanId"));
        assertThat(response.getHeaderString(Traces.HttpHeaders.SPAN_ID), is("spanId"));
    }

    public static class TracingTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            JerseyServers.configure(env.jersey().getResourceConfig(),
                    ExceptionMappers.StacktracePropagation.DO_NOT_PROPAGATE);
            env.jersey().register(new TracingTestResource());
        }
    }

    public static final class TracingTestResource implements TracingTestService {
        @Override
        public String getTraceOperation() {
            return Traces.getTrace().get().getOperation();
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TracingTestService {
        @GET
        @Path("/trace")
        String getTraceOperation();
    }
}
