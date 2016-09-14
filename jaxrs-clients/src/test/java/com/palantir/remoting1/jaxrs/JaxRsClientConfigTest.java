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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.github.kristofa.brave.IdConversion;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.palantir.remoting1.clients.ClientConfig;
import com.palantir.remoting1.config.ssl.SslConfiguration;
import com.palantir.remoting1.config.ssl.SslSocketFactories;
import com.palantir.remoting1.servers.BraveTracer;
import com.palantir.remoting1.servers.ProxyingEchoServer;
import com.palantir.remoting1.servers.TestEchoServer;
import com.palantir.remoting1.servers.Tracer;
import com.palantir.remoting1.servers.Tracers;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.assertj.core.util.Maps;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class JaxRsClientConfigTest {

    private static final String CONFIG_PATH = "src/test/resources/test-server-ssl.yml";

    @ClassRule
    public static final DropwizardAppRule<Configuration> ECHO_SERVER =
            new DropwizardAppRule<>(TestEchoServer.class, CONFIG_PATH);

    @ClassRule
    public static final DropwizardAppRule<Configuration> PROXYING_ECHO_SERVER =
            new DropwizardAppRule<>(ProxyingEchoServer.class, CONFIG_PATH);

    @Mock
    private Appender<ILoggingEvent> tracingAppender;

    // Used by #setLogLevel to keep track of original/default log levels so they can be reset in #after()
    private Map<String, Level> originalLogLevels = Maps.newHashMap();

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        when(tracingAppender.getName()).thenReturn("MOCK");
        ch.qos.logback.classic.Logger clientTracerLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("tracing");
        clientTracerLogger.addAppender(tracingAppender);

        // connect proxy to actual echo server dynamic port
        ((ProxyingEchoServer) PROXYING_ECHO_SERVER.getApplication()).setEchoServerPort(ECHO_SERVER.getLocalPort());
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
    public void testBraveTracing_clientLogsTraces() throws Exception {
        setLogLevel("com.palantir.remoting1", Level.DEBUG);
        setLogLevel("tracing", Level.TRACE);
        TestEchoService service = createProxy(PROXYING_ECHO_SERVER.getLocalPort(), "test");

        assertThat(service.echo("foo"), is("foo"));

        ArgumentCaptor<ILoggingEvent> tracingLoggingEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(tracingAppender, atLeastOnce()).doAppend(tracingLoggingEvent.capture());
        List<ILoggingEvent> traceLoggingEvents = tracingLoggingEvent.getAllValues();
        assertThat(traceLoggingEvents, Matchers.<ILoggingEvent>iterableWithSize(6));

        // expect 2 client send "cs" traces -- one for test to proxy, one for proxy to echo service
        assertThat(FluentIterable.from(traceLoggingEvents).filter(new Predicate<ILoggingEvent>() {
                    @Override
                    public boolean apply(ILoggingEvent event) {
                        return event != null && event.getMessage().contains("\"value\":\"cs\"");
                    }
                }),
                Matchers.<ILoggingEvent>iterableWithSize(2));

        // expect 2 client receive "cr" traces -- one for test from proxy, one for proxy from echo service
        assertThat(FluentIterable.from(traceLoggingEvents).filter(new Predicate<ILoggingEvent>() {
                    @Override
                    public boolean apply(ILoggingEvent event) {
                        return event != null && event.getMessage().contains("\"value\":\"cr\"");
                    }
                }),
                Matchers.<ILoggingEvent>iterableWithSize(2));

        Mockito.verifyNoMoreInteractions(tracingAppender);
    }

    @Test
    public void testBraveTracing_traceIdsAreCarriedForward() throws Exception {
        setLogLevel("com.palantir.remoting1", Level.DEBUG);
        setLogLevel("tracing", Level.TRACE);

        // Simulates two-hop call chain: client --> ProxyingEchoServer --> EchoServer.
        // Verifies that trace ids logged in the three locations are identical.

        Tracer originalTracer = Tracers.activeTracer();
        TestEchoService service = createProxy(PROXYING_ECHO_SERVER.getLocalPort(), "test");

        assertThat(originalTracer, is(Tracers.activeTracer()));
        assertThat(originalTracer, instanceOf(BraveTracer.class));
        assertThat(MDC.get("traceId"), is(nullValue()));

        String foo = service.echo("foo");

        assertThat(foo, is("foo"));

        assertThat(originalTracer, is(Tracers.activeTracer()));
        assertThat(originalTracer, instanceOf(BraveTracer.class));
        assertThat(MDC.get("traceId"), is(nullValue()));

        // expect 6 traces - 2 local, 2 client, 2 server
        ArgumentCaptor<ILoggingEvent> tracingLoggingEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(tracingAppender, times(6)).doAppend(tracingLoggingEvent.capture());
        List<ILoggingEvent> traceLoggingEvents = tracingLoggingEvent.getAllValues();
        assertThat(traceLoggingEvents, hasSize(6));
        Set<String> traceIds = new HashSet<>();
        for (ILoggingEvent traceLoggingEvent : traceLoggingEvents) {
            System.out.printf("Captured logging event to %s:%n  %s%n",
                    traceLoggingEvent.getLoggerName(), traceLoggingEvent.getFormattedMessage());
            String message = traceLoggingEvent.getMessage();
            String traceId = getTraceIdFromLogMessage(message);
            traceIds.add(traceId);
            long longTraceId = IdConversion.convertToLong(traceId);
            assertThat("Message trace ID is not null for '" + message + "'", traceId, is(notNullValue()));

            String mdcTraceId = traceLoggingEvent.getMDCPropertyMap().get("traceId");
            if (mdcTraceId != null) {
                long longMdcTraceId = IdConversion.convertToLong(mdcTraceId);
                assertThat("Same trace IDs for log message '" + traceLoggingEvent.getFormattedMessage() + "'",
                        longTraceId, is(longMdcTraceId));
            }
        }

        assertThat(traceIds, hasSize(1));
    }

    private static final Pattern tracePattern = Pattern.compile(".*traceId\":\"([a-z0-9]+).*");

    private static String getTraceIdFromLogMessage(String message) {
        Matcher matcher = tracePattern.matcher(message);
        assertTrue(matcher.matches());
        return matcher.group(1);
    }

    private static TestEchoService createProxy(int port, String name) {
        String endpointUri = "https://localhost:" + port;
        SslConfiguration sslConfig = SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks"));
        return JaxRsClient.builder(
                ClientConfig.builder()
                        .trustContext(SslSocketFactories.createTrustContext(sslConfig))
                        .build())
                .build(TestEchoService.class, name, endpointUri);
    }

}
