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

package com.palantir.remoting1.jaxrs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.palantir.remoting1.clients.ClientConfig;
import com.palantir.remoting1.config.ssl.SslConfiguration;
import com.palantir.remoting1.config.ssl.SslSocketFactories;
import com.palantir.remoting1.servers.dropwizard.DropwizardServers;
import com.palantir.remoting1.servers.jersey.ExceptionMappers;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.nio.file.Paths;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.assertj.core.util.Maps;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mock;
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

    // Used by #setLogLevel to keep track of original/default log levels so they can be reset in #after()
    private Map<String, Level> originalLogLevels = Maps.newHashMap();

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        when(clientTracerAppender.getName()).thenReturn("MOCK");
        ch.qos.logback.classic.Logger clientTracerLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("tracing.client.test");
        clientTracerLogger.addAppender(clientTracerAppender);

        when(serverTracerAppender.getName()).thenReturn("MOCK");
        ch.qos.logback.classic.Logger serverTracerLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("tracing.server.TestEchoServer");
        serverTracerLogger.addAppender(serverTracerAppender);

        when(proxyingServerTracerAppender.getName()).thenReturn("MOCK");
        ch.qos.logback.classic.Logger proxyingServerTracerLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("tracing.server.ProxyingEchoServer");
        proxyingServerTracerLogger.addAppender(proxyingServerTracerAppender);
    }

    @After
    public void after() {
        for (Map.Entry<String, Level> level : originalLogLevels.entrySet()) {
            getLogbackLogger(level.getKey()).setLevel(level.getValue());
        }
    }

    private void setLogLevel(String name, Level level) {
        if (!originalLogLevels.containsKey(name)) {
            originalLogLevels.put(name, level);
        }
        getLogbackLogger(name).setLevel(level);
    }

    private static ch.qos.logback.classic.Logger getLogbackLogger(String name) {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
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
        TestEchoService service = createProxy(ECHO_SERVER.getLocalPort(), "test");
        assertThat(service.echo("foo"), is("foo"));
    }

    @Test
    public void testRetries_notSupported() throws Exception {
        try {
            JaxRsClient.builder(ClientConfig.builder().maxNumRetries(1).build())
                    .build(TestEchoService.class, "agent", "uri");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Connection-level retries are not supported by JaxRsClient"));
        }
    }

    private static TestEchoService createProxy(int port, String name) {
        String endpointUri = "https://localhost:" + port;
        SslConfiguration sslConfig = SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks"));
        return JaxRsClient.builder(
                ClientConfig.builder().trustContext(SslSocketFactories.createTrustContext(sslConfig)).build())
                .build(TestEchoService.class, name, endpointUri);
    }

    public static final class ProxyingEchoServer extends Application<Configuration> {
        @Override
        public void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(new TestEchoResource());
            DropwizardServers.configure(env, config, ProxyingEchoServer.class.getSimpleName(),
                    ExceptionMappers.StacktracePropagation.DO_NOT_PROPAGATE);
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
            DropwizardServers.configure(env, config, TestEchoServer.class.getSimpleName(),
                    ExceptionMappers.StacktracePropagation.DO_NOT_PROPAGATE);
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
