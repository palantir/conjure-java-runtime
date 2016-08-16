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

package com.palantir.remoting1.servers;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import com.github.kristofa.brave.http.BraveHttpHeaders;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

public final class DropwizardTracingFiltersTest {
    private static final ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(DropwizardTracingFiltersTest.class);

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(TestEchoServer.class,
            "src/test/resources/test-server.yml");

    private WebTarget target;
    @Mock
    private Appender<ILoggingEvent> braveMockAppender;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);

        when(braveMockAppender.getName()).thenReturn("MOCK");
        // the logger used by the brave server instance
        ch.qos.logback.classic.Logger braveLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ServerTracer(testTracerName)");
        braveLogger.addAppender(braveMockAppender);
    }

    @Test
    public void testBraveTracing_serverLogsTraceId() throws Exception {
        target.path("echo").request().header(BraveHttpHeaders.TraceId.getName(), "myTraceId").get();

        ArgumentCaptor<ILoggingEvent> requestEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(braveMockAppender).doAppend(requestEvent.capture());
        assertThat(requestEvent.getValue().getFormattedMessage(),
                containsString("\"serviceName\":\"testtracername\",\"ipv4\":\"0.0.0.0\",\"port\":61827}"));
        Mockito.verifyNoMoreInteractions(braveMockAppender);
    }

    @Test
    public void testLogAppenderCanAccessTraceId() throws Exception {
        // Augment logger with custom appender whose output we can read
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder ple = new PatternLayoutEncoder();
        ple.setPattern("traceId: %X{traceId}");
        ple.setContext(lc);
        ple.start();
        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setEncoder(ple);
        appender.setContext(lc);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        appender.setOutputStream(byteStream);
        appender.start();
        logger.addAppender(appender);

        // Invoke server and observe servers log messages; note that the server uses the same logger.
        target.path("echo").request().header(BraveHttpHeaders.TraceId.getName(), "myTraceId").get();
        assertThat(byteStream.toString(StandardCharsets.UTF_8.name()), containsString("traceId: myTraceId"));
    }

    public static final class TestEchoServer extends Application<Configuration> {
        @Override
        public void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(new TestEchoResource());
            DropwizardServers.configure(env, config, "testTracerName", true);
        }

        public static final class TestEchoResource implements TestEchoService {
            @Override
            public String echo(String value) {
                logger.info("test log message");
                return value;
            }
        }
    }

    @Path("/")
    public interface TestEchoService {
        @GET
        @Path("/echo")
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        String echo(@QueryParam("value") String value);
    }
}
