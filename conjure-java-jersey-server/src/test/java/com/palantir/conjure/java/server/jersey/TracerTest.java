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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.palantir.conjure.java.api.tracing.TraceHttpHeaders;
import com.palantir.conjure.java.tracing.Tracer;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public final class TracerTest {

    private static final ch.qos.logback.classic.Logger log =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TracerTest.class);

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(TracingTestServer.class, "src/test/resources/test-server.yml");

    private WebTarget target;
    private Level previousLoggerLevel;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
        previousLoggerLevel = log.getLevel();
    }

    @After
    public void after() throws Exception {
        log.setLevel(previousLoggerLevel);
    }

    @Test
    public void testTracingFilterIsApplied() {
        Response response = target.path("/trace").request()
                .header(TraceHttpHeaders.TRACE_ID, "traceId")
                .header(TraceHttpHeaders.PARENT_SPAN_ID, "parentSpanId")
                .header(TraceHttpHeaders.SPAN_ID, "spanId")
                .get();
        Assert.assertThat(response.getStatus(), Matchers.is(Status.OK.getStatusCode()));
        Assert.assertThat(response.readEntity(String.class), Matchers.is("traceId"));
        Assert.assertThat(response.getHeaderString(TraceHttpHeaders.TRACE_ID), Matchers.is("traceId"));
        Assert.assertNull(response.getHeaderString(TraceHttpHeaders.SPAN_ID));
        Assert.assertNull(response.getHeaderString(TraceHttpHeaders.PARENT_SPAN_ID));
    }

    @Test
    public void testLogAppenderCanAccessTraceId() throws Exception {
        // Augment logger with custom appender whose output we can read
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder ple = new PatternLayoutEncoder();
        ple.setPattern("traceId: %X{traceId} %-5level [%thread]: %message%n");
        ple.setContext(lc);
        ple.start();
        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setEncoder(ple);
        appender.setContext(lc);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        appender.setOutputStream(byteStream);
        appender.start();
        log.addAppender(appender);

        // Invoke server and observe servers log messages; note that the server uses the same logger at INFO.
        log.setLevel(Level.INFO);
        target.path("trace").request().header(TraceHttpHeaders.TRACE_ID, "myTraceId").get();
        Assert.assertThat(
                byteStream.toString(StandardCharsets.UTF_8.name()),
                Matchers.startsWith("traceId: myTraceId"));
    }


    public static class TracingTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(ConjureJerseyFeature.INSTANCE);
            env.jersey().register(new TracingTestResource());
        }
    }

    public static final class TracingTestResource implements TracingTestService {
        @Override
        public String getTraceOperation() {
            log.info("foo");
            return Tracer.getTraceId();
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
