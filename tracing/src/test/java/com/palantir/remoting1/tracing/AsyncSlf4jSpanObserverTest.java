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

package com.palantir.remoting1.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import java.net.Inet4Address;
import java.nio.charset.StandardCharsets;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import zipkin.Annotation;
import zipkin.Codec;
import zipkin.Endpoint;

public final class AsyncSlf4jSpanObserverTest {

    private static final AsyncSlf4jSpanObserver.ZipkinCompatEndpoint DUMMY_ENDPOINT =
            ImmutableZipkinCompatEndpoint.builder()
                    .serviceName("")
                    .ipv4("0.0.0.0")
                    .build();

    @Mock
    private Appender<ILoggingEvent> appender;
    @Captor
    private ArgumentCaptor<ILoggingEvent> event;

    private Logger logger;
    private Level originalLevel;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);

        when(appender.getName()).thenReturn("MOCK");
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AsyncSlf4jSpanObserver.class);
        logger.addAppender(appender);

        originalLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
    }

    @After
    public void after() {
        logger.setLevel(originalLevel);
    }

    @Test
    public void testJsonFormatToLog() throws Exception {
        DeterministicScheduler executor = new DeterministicScheduler();
        Tracer.subscribe("", AsyncSlf4jSpanObserver.of(
                "serviceName", Inet4Address.getLoopbackAddress(), logger, executor));
        Tracer.startLocalSpan("operation");
        Span span = Tracer.completeSpan().get();
        verify(appender, never()).doAppend(any(ILoggingEvent.class)); // async logger only fires when executor runs

        executor.runNextPendingCommand();
        assertThat(executor.isIdle()).isTrue();
        verify(appender).doAppend(event.capture());
        AsyncSlf4jSpanObserver.ZipkinCompatEndpoint expectedEndpoint = ImmutableZipkinCompatEndpoint.builder()
                .serviceName("serviceName")
                .ipv4("127.0.0.1")
                .build();
        assertThat(event.getValue().getFormattedMessage())
                .isEqualTo(AsyncSlf4jSpanObserver.ZipkinCompatSpan.fromSpan(span, expectedEndpoint).toJson());
        verifyNoMoreInteractions(appender);
        Tracer.unsubscribe("");
    }

    @Test
    public void testDefaultConstructorDeterminesIpAddress() throws Exception {
        DeterministicScheduler executor = new DeterministicScheduler();
        Tracer.subscribe("", AsyncSlf4jSpanObserver.of("serviceName", executor));
        Tracer.startLocalSpan("operation");
        Span span = Tracer.completeSpan().get();

        executor.runNextPendingCommand();
        verify(appender).doAppend(event.capture());
        AsyncSlf4jSpanObserver.ZipkinCompatEndpoint expectedEndpoint = ImmutableZipkinCompatEndpoint.builder()
                .serviceName("serviceName")
                .ipv4(InetAddressSupplier.INSTANCE.get().getHostAddress())
                .build();
        assertThat(event.getValue().getFormattedMessage())
                .isEqualTo(AsyncSlf4jSpanObserver.ZipkinCompatSpan.fromSpan(span, expectedEndpoint).toJson());
        Tracer.unsubscribe("");
    }

    @Test
    public void testSpanLogFormatIsZipkinCompatible() throws Exception {
        Span span = Span.builder()
                .traceId(Tracers.longToPaddedHex(42L))
                .parentSpanId(Tracers.longToPaddedHex(123456789L))
                .spanId(Tracers.longToPaddedHex(234567890L))
                .operation("op")
                .startTimeMicroSeconds(43L)
                .durationNanoSeconds(43001L) // will round up to 44 microseconds
                .type(SpanType.CLIENT_OUTGOING)
                .build();
        zipkin.Span zipkinSpan = zipkin.Span.builder()
                .traceId(42L)
                .parentId(123456789L)
                .id(234567890L)
                .name("op")
                .timestamp(43L)  // micro-seconds
                .duration(44L)  // micro-seconds, rounded up
                .addAnnotation(Annotation.create(43L, "cs", Endpoint.create("service", 2)))
                .addAnnotation(Annotation.create(87L, "cr", Endpoint.create("service", 2)))
                .build();
        String expectedString = new String(Codec.JSON.writeSpan(zipkinSpan), StandardCharsets.UTF_8);
        AsyncSlf4jSpanObserver.ZipkinCompatEndpoint actualEndpoint = ImmutableZipkinCompatEndpoint.builder()
                .serviceName("service")
                .ipv4("0.0.0.2")
                .build();
        String actualString = AsyncSlf4jSpanObserver.ZipkinCompatSpan.fromSpan(span, actualEndpoint).toJson();
        assertThat(actualString).isEqualTo(expectedString);
    }

    @Test
    public void testSpanTypesConvertToZipkinAnnotations() throws Exception {
        assertThat(zipkinSpan(10, 1001, SpanType.CLIENT_OUTGOING).annotations()).containsExactly(
                annotation("cs", 10),
                annotation("cr", 12)); // 10 + roundup(1001/10) == 10+2

        assertThat(zipkinSpan(10, 999, SpanType.CLIENT_OUTGOING).annotations()).containsExactly(
                annotation("cs", 10),
                annotation("cr", 11)); // 10 + roundup(999/10) == 10+1

        assertThat(zipkinSpan(10, 1001, SpanType.SERVER_INCOMING).annotations()).containsExactly(
                annotation("sr", 10),
                annotation("ss", 12)); // 10 + roundup(1001/10) == 10+2

        assertThat(zipkinSpan(10, 999, SpanType.SERVER_INCOMING).annotations()).containsExactly(
                annotation("sr", 10),
                annotation("ss", 11)); // 10 + roundup(999/10) == 10+1
    }

    @Test
    public void testNanoToMicro() throws Exception {
        // must always round up, in particular 0ns --> 1ms
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatSpan.nanoToMicro(0)).isEqualTo(1);
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatSpan.nanoToMicro(1)).isEqualTo(1);
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatSpan.nanoToMicro(1499)).isEqualTo(2);
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatSpan.nanoToMicro(1500)).isEqualTo(2);
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatSpan.nanoToMicro(1501)).isEqualTo(2);
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatSpan.nanoToMicro(2000)).isEqualTo(3);
    }

    private static AsyncSlf4jSpanObserver.ZipkinCompatSpan zipkinSpan(long start, long duration, SpanType type) {
        Span span = Span.builder()
                .traceId("")
                .spanId("")
                .operation("")
                .startTimeMicroSeconds(start)
                .durationNanoSeconds(duration)
                .type(type)
                .build();
        return AsyncSlf4jSpanObserver.ZipkinCompatSpan.fromSpan(span, DUMMY_ENDPOINT);
    }

    private static AsyncSlf4jSpanObserver.ZipkinCompatAnnotation annotation(String value, long timestamp) {
        return AsyncSlf4jSpanObserver.ZipkinCompatAnnotation.of(timestamp, value, DUMMY_ENDPOINT);
    }
}
