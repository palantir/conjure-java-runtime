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

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.github.kristofa.brave.AnnotationSubmitter;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BraveTracerTest {
    private static final Logger log = LoggerFactory.getLogger(BraveTracerTest.class);

    private SpanCollector mockSpanCollector;
    private BraveTracer tracer;

    @Before
    public void before() throws Exception {
        mockSpanCollector = mock(SpanCollector.class);

        Brave brave = new Brave.Builder(new InheritableServerClientAndLocalSpanState(
                Endpoint.create("test", 127 << 4 | 1, 0)))
                .traceSampler(Sampler.ALWAYS_SAMPLE)
                .spanCollector(mockSpanCollector)
                .build();
        tracer = new BraveTracer(brave);
    }

    @Test
    public void beginTrace() throws Exception {
        Tracer.TraceContext context = tracer.begin("component", "operation");
        assertThat(context, instanceOf(BraveContext.class));
        verify(mockSpanCollector, never()).collect(any(Span.class));

        context.close();

        verify(mockSpanCollector, times(1)).collect(any(Span.class));
        verifyNoMoreInteractions(mockSpanCollector);
    }

    @Test
    public void traceRunnable() throws Exception {
        Runnable mockRunnable = mock(Runnable.class);

        Tracers.setActiveTracer(tracer);

        Tracer.TraceContext context = tracer.begin("component", "operation");
        assertThat(context, is(notNullValue()));
        Tracers.trace("component", "operation", mockRunnable);

        verify(mockRunnable, times(1)).run();
        verify(mockSpanCollector, times(1)).collect(any(Span.class));
        verifyNoMoreInteractions(mockSpanCollector, mockRunnable);
    }

    @Test
    public void traceCallable() throws Exception {
        @SuppressWarnings("unchecked") // yay generic type erasure
        Callable<String> mockCallable = mock(Callable.class);
        when(mockCallable.call()).thenReturn("result");

        Tracers.setActiveTracer(tracer);
        String result = Tracers.trace("component", "operation", mockCallable);

        assertThat(result, is("result"));
        verify(mockCallable, times(1)).call();
        verify(mockSpanCollector, times(1)).collect(any(Span.class));
        verifyNoMoreInteractions(mockSpanCollector, mockCallable);
    }

    @Test
    public void defaultClock() throws Exception {
        AnnotationSubmitter.Clock clock = BraveTracer.defaultClock();
        assertThat(clock, is(notNullValue()));

        long startMicros = clock.currentTimeMicroseconds();
        long timeMillis = System.currentTimeMillis();
        long elapsedMicros = toMicrosFromMillis(timeMillis) - startMicros;

        assertThat(elapsedMicros, is(0L));
    }

    private static long toMicrosFromMillis(long timeMillis) {
        return TimeUnit.MICROSECONDS.convert(timeMillis, TimeUnit.MILLISECONDS);
    }
}
