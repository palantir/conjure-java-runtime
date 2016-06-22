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

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
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

public final class DropwizardBraveTracingFiltersTest {
    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(TestEchoServer.class,
            "src/test/resources/test-server.yml");

    private WebTarget target;
    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);

        when(mockAppender.getName()).thenReturn("MOCK");
        ch.qos.logback.classic.Logger resourceLog =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ServerTracer(testTracerName)");
        resourceLog.addAppender(mockAppender);
    }

    @Test
    public void testBraveTracing() throws Exception {
        target.path("echo").request().get();

        ArgumentCaptor<ILoggingEvent> requestEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(mockAppender).doAppend(requestEvent.capture());
        assertThat(requestEvent.getValue().getFormattedMessage(),
                containsString("\"serviceName\":\"testtracername\",\"ipv4\":\"0.0.0.0\",\"port\":61827}"));
        Mockito.verifyNoMoreInteractions(mockAppender);
    }

    public static final class TestEchoServer extends Application<Configuration> {
        @Override
        public void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(new TestEchoResource());
            DropwizardBraveTracingFilters.registerBraveTracers(env.jersey(), config, "testTracerName");
        }

        public static final class TestEchoResource implements TestEchoService {
            @Override
            public String echo(String value) {
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
