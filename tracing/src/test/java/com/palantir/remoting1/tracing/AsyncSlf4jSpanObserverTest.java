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
import zipkin.Codec;

public final class AsyncSlf4jSpanObserverTest {

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
        Tracer.subscribe("foo", AsyncSlf4jSpanObserver.of(executor));
        Tracer.startSpan("operation");
        Span span = Tracer.completeSpan().get();
        verify(appender, never()).doAppend(any(ILoggingEvent.class)); // async logger only fires when executor runs

        executor.runNextPendingCommand();
        assertThat(executor.isIdle()).isTrue();
        verify(appender).doAppend(event.capture());
        assertThat(event.getValue().getFormattedMessage())
                .isEqualTo(AsyncSlf4jSpanObserver.ZipkinCompatibleSerializableSpan.fromSpan(span).toJson());
        verifyNoMoreInteractions(appender);
    }

    @Test
    public void testLogFormatIsZipkinCompatible() throws Exception {
        Span span = Span.builder()
                .traceId(Tracers.longToPaddedHex(42L))
                .parentSpanId(Tracers.longToPaddedHex(123456789L))
                .spanId(Tracers.longToPaddedHex(234567890L))
                .operation("op")
                .startTimeMicroSeconds(43L)
                .durationNanoSeconds(43001L)
                .build();
        zipkin.Span zipkinSpan = zipkin.Span.builder()
                .traceId(42L)
                .parentId(123456789L)
                .id(234567890L)
                .name("op")
                .timestamp(43L)  // micro-seconds
                .duration(44L)  // micro-seconds, rounded up
                .build();
        String expectedString = new String(Codec.JSON.writeSpan(zipkinSpan), StandardCharsets.UTF_8);
        String actualString = AsyncSlf4jSpanObserver.ZipkinCompatibleSerializableSpan.fromSpan(span).toJson();
        assertThat(actualString).isEqualTo(expectedString);
    }

    @Test
    public void testNanoToMicro() throws Exception {
        // must always round up, in particular 0ns --> 1ms
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatibleSerializableSpan.nanoToMicro(0)).isEqualTo(1);
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatibleSerializableSpan.nanoToMicro(1)).isEqualTo(1);
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatibleSerializableSpan.nanoToMicro(1499)).isEqualTo(2);
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatibleSerializableSpan.nanoToMicro(1500)).isEqualTo(2);
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatibleSerializableSpan.nanoToMicro(1501)).isEqualTo(2);
        assertThat(AsyncSlf4jSpanObserver.ZipkinCompatibleSerializableSpan.nanoToMicro(2000)).isEqualTo(2);
    }
}
