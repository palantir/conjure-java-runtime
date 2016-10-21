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
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

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
    public void testSanity() throws Exception {
        DeterministicScheduler executor = new DeterministicScheduler();
        Tracer.subscribe("foo", AsyncSlf4jSpanObserver.of(executor));
        Tracer.startSpan("operation");
        Tracer.completeSpan();
        verify(appender, never()).doAppend(any(ILoggingEvent.class)); // async logger only fires when executor runs

        executor.runNextPendingCommand();
        assertThat(executor.isIdle()).isTrue();
        verify(appender).doAppend(event.capture());
        assertThat(event.getValue().getFormattedMessage()).containsSequence("traceId=" + Tracer.getTraceId());
        verifyNoMoreInteractions(appender);
    }
}
