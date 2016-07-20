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

package com.palantir.remoting.jaxrs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.palantir.remoting.http.server.DropwizardTracingFilters;
import com.palantir.remoting.ssl.SslConfiguration;
import com.palantir.remoting.ssl.SslSocketFactories;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

public final class JaxRsClientConfigTest {
    @ClassRule
    public static final DropwizardAppRule<Configuration> ECHO_SERVER =
            new DropwizardAppRule<>(TestEchoServer.class, "src/test/resources/test-server-ssl.yml");

    @ClassRule
    public static final DropwizardAppRule<Configuration> PROXYING_ECHO_SERVER =
            new DropwizardAppRule<>(ProxyingEchoServer.class, "src/test/resources/test-server-ssl.yml");

    @Mock
    private Appender<ILoggingEvent> clientTracerAppender;
    @Mock
    private Appender<ILoggingEvent> serverTracerAppender;
    @Mock
    private Appender<ILoggingEvent> proxyingServerTracerAppender;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        when(clientTracerAppender.getName()).thenReturn("MOCK");
        ch.qos.logback.classic.Logger clientTracerLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ClientTracer(client)");
        clientTracerLogger.addAppender(clientTracerAppender);

        when(serverTracerAppender.getName()).thenReturn("MOCK");
        ch.qos.logback.classic.Logger serverTracerLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ServerTracer(TestEchoServer)");
        serverTracerLogger.addAppender(serverTracerAppender);

        when(proxyingServerTracerAppender.getName()).thenReturn("MOCK");
        ch.qos.logback.classic.Logger proxyingServerTracerLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ServerTracer(ProxyingEchoServer)");
        proxyingServerTracerLogger.addAppender(proxyingServerTracerAppender);
    }

    @Test
    public void testSslSocketFactory_cannotConnectWhenSocketFactoryIsNotSet() throws Exception {
        String endpointUri = "https://localhost:" + ECHO_SERVER.getLocalPort();
        TestEchoService service = JaxRsClient.builder().build(TestEchoService.class, "agent", endpointUri);

        try {
            service.echo("foo");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(),
                    containsString("sun.security.validator.ValidatorException: PKIX path building failed:"));
        }
    }

    @Test
    public void testSslSocketFactory_canConnectWhenSocketFactoryIsSet() throws Exception {
        TestEchoService service = createProxy(ECHO_SERVER.getLocalPort(), "client");
        assertThat(service.echo("foo"), is("foo"));
    }

    @Test
    public void testBraveTracing_clientLogsTraces() throws Exception {
        TestEchoService service = createProxy(PROXYING_ECHO_SERVER.getLocalPort(), "client");
        assertThat(service.echo("foo"), is("foo"));

        ArgumentCaptor<ILoggingEvent> clientTracerEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(clientTracerAppender).doAppend(clientTracerEvent.capture());
        assertThat(clientTracerEvent.getValue().getFormattedMessage(), containsString("\"serviceName\":\"client\","));
        Mockito.verifyNoMoreInteractions(clientTracerAppender);
    }

    @Test
    public void testBraveTracing_traceIdsAreCarriedForward() throws Exception {
        // Simulates two-hop call chain: client --> ProxyingEchoServer --> EchoServer. Verifies that
        // trace ids logged in the three locations are identical.

        TestEchoService service = createProxy(PROXYING_ECHO_SERVER.getLocalPort(), "client");
        assertThat(service.echo("foo"), is("foo"));


        // Extract client trace id.
        ArgumentCaptor<ILoggingEvent> clientTracerEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(clientTracerAppender).doAppend(clientTracerEvent.capture());
        String clientTraceId = getTraceIdFromLogEvent(clientTracerEvent);
        Mockito.verifyNoMoreInteractions(clientTracerAppender);

        // Verify client and proxying echo server trace ids are identical
        ArgumentCaptor<ILoggingEvent> proxyingServerTracerEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(proxyingServerTracerAppender).doAppend(proxyingServerTracerEvent.capture());
        assertThat(clientTraceId, is(getTraceIdFromLogEvent(proxyingServerTracerEvent)));
        Mockito.verifyNoMoreInteractions(proxyingServerTracerAppender);

        // Verify client and echo server trace ids are identical
        ArgumentCaptor<ILoggingEvent> serverTracerEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(serverTracerAppender).doAppend(serverTracerEvent.capture());
        assertThat(clientTraceId, is(getTraceIdFromLogEvent(serverTracerEvent)));
        Mockito.verifyNoMoreInteractions(serverTracerAppender);
    }

    private String getTraceIdFromLogEvent(ArgumentCaptor<ILoggingEvent> event) {
        Pattern tracePattern = Pattern.compile(".*traceId\":\"([a-z0-9]+).*");
        Matcher matcher = tracePattern.matcher(event.getValue().getFormattedMessage());
        assertTrue(matcher.matches());
        return matcher.group(1);
    }

    private static TestEchoService createProxy(int port, String name) {
        String endpointUri = "https://localhost:" + port;
        SslConfiguration sslConfig = SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks"));
        return JaxRsClient.builder(
                ClientConfig.empty()
                        .ssl(SslSocketFactories.createSslSocketFactory(sslConfig),
                                (X509TrustManager) SslSocketFactories.createTrustManagers(sslConfig)[0]))
                .build(TestEchoService.class, name, endpointUri);
    }

    public static final class ProxyingEchoServer extends Application<Configuration> {
        @Override
        public void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(new TestEchoResource());
            DropwizardTracingFilters.registerTracers(env, config, ProxyingEchoServer.class.getSimpleName());
        }

        private static final class TestEchoResource implements TestEchoService {
            @Override
            public String echo(String value) {
                TestEchoService echoService = createProxy(ECHO_SERVER.getLocalPort(), "proxyingClient");
                return echoService.echo(value);
            }
        }
    }

    public static final class TestEchoServer extends Application<Configuration> {
        @Override
        public void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(new TestEchoResource());
            DropwizardTracingFilters.registerTracers(env, config, TestEchoServer.class.getSimpleName());
        }

        private static final class TestEchoResource implements TestEchoService {
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
