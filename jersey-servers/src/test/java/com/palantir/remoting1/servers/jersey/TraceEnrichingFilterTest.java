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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;

import com.palantir.remoting1.tracing.Span;
import com.palantir.remoting1.tracing.SpanObserver;
import com.palantir.remoting1.tracing.TraceHttpHeaders;
import com.palantir.remoting1.tracing.Tracer;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
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
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class TraceEnrichingFilterTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(TracingTestServer.class, "src/test/resources/test-server.yml");

    @Captor
    private ArgumentCaptor<Span> span;

    @Mock
    private SpanObserver observer;

    private WebTarget target;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
        Tracer.subscribe("", observer);
    }

    @After
    public void after() {
        Tracer.unsubscribe("");
    }

    @Test
    public void testTraceState_withHeaderUsesTraceId() {
        Response response = target.path("/trace").request()
                .header(TraceHttpHeaders.TRACE_ID, "traceId")
                .header(TraceHttpHeaders.PARENT_SPAN_ID, "parentSpanId")
                .header(TraceHttpHeaders.SPAN_ID, "spanId")
                .get();
        assertThat(response.getHeaderString(TraceHttpHeaders.TRACE_ID), is("traceId"));
        assertThat(response.getHeaderString(TraceHttpHeaders.PARENT_SPAN_ID), is(nullValue()));
        assertThat(response.getHeaderString(TraceHttpHeaders.SPAN_ID), is(nullValue()));
        verify(observer).consume(span.capture());
        assertThat(span.getValue().getOperation(), is("GET /trace"));
    }

    @Test
    public void testTraceState_respectsMethod() {
        Response response = target.path("/trace").request()
                .header(TraceHttpHeaders.TRACE_ID, "traceId")
                .header(TraceHttpHeaders.PARENT_SPAN_ID, "parentSpanId")
                .header(TraceHttpHeaders.SPAN_ID, "spanId")
                .post(Entity.json(""));
        assertThat(response.getHeaderString(TraceHttpHeaders.TRACE_ID), is("traceId"));
        assertThat(response.getHeaderString(TraceHttpHeaders.PARENT_SPAN_ID), is(nullValue()));
        assertThat(response.getHeaderString(TraceHttpHeaders.SPAN_ID), is(nullValue()));
        verify(observer).consume(span.capture());
        assertThat(span.getValue().getOperation(), is("POST /trace"));
    }

    @Test
    public void testTraceState_withoutRequestHeadersGeneratesValidTraceResponseHeaders() {
        Response response = target.path("/trace").request().get();
        assertThat(response.getHeaderString(TraceHttpHeaders.TRACE_ID), not(nullValue()));
        assertThat(response.getHeaderString(TraceHttpHeaders.PARENT_SPAN_ID), is(nullValue()));
        assertThat(response.getHeaderString(TraceHttpHeaders.SPAN_ID), is(nullValue()));
        verify(observer).consume(span.capture());
        assertThat(span.getValue().getOperation(), is("GET /trace"));
    }

    @Test
    public void testTraceState_withEmptyTraceIdGeneratesValidTraceResponseHeaders() {
        Response response = target.path("/trace").request().header(TraceHttpHeaders.TRACE_ID, "").get();
        assertThat(response.getHeaderString(TraceHttpHeaders.TRACE_ID), not(nullValue()));
        assertThat(response.getHeaderString(TraceHttpHeaders.PARENT_SPAN_ID), is(nullValue()));
        assertThat(response.getHeaderString(TraceHttpHeaders.SPAN_ID), is(nullValue()));
        verify(observer).consume(span.capture());
        assertThat(span.getValue().getOperation(), is("GET /trace"));
    }

    public static class TracingTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(new TraceEnrichingFilter());
            env.jersey().register(new TracingTestResource());
        }
    }

    public static final class TracingTestResource implements TracingTestService {
        @Override
        public void getTraceOperation() {}

        @Override
        public void postTraceOperation() {}
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TracingTestService {
        @GET
        @Path("/trace")
        void getTraceOperation();

        @POST
        @Path("/trace")
        void postTraceOperation();
    }
}
