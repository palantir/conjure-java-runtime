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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public final class TracerTest {

    @Mock
    private SpanObserver observer1;
    @Mock
    private SpanObserver observer2;
    @Mock
    private TraceSampler sampler;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void after() {
        Tracer.initTrace(Optional.of(true), Tracers.randomId());
        Tracer.setSampler(AlwaysSampler.INSTANCE);
        Tracer.unsubscribe(observer1);
        Tracer.unsubscribe(observer2);
    }

    @Test
    public void testIdsMustBeNonNullAndNotEmpty() throws Exception {
        try {
            Tracer.initTrace(Optional.<Boolean>absent(), null);
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("traceId must be non-empty: null");
        }

        try {
            Tracer.initTrace(Optional.<Boolean>absent(), "");
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("traceId must be non-empty: ");
        }

        try {
            Tracer.startSpan("op", null);
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("parentTraceId must be non-empty: null");
        }

        try {
            Tracer.startSpan("op", "");
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessage("parentTraceId must be non-empty: ");
        }
    }

    @Test
    public void testSubscribeUnsubscribe() throws Exception {
        // no error when completing span without a registered subscriber
        startAndCompleteSpan();

        Tracer.subscribe(observer1);
        Tracer.subscribe(observer2);
        Span span = startAndCompleteSpan();
        verify(observer1).consume(span);
        verify(observer2).consume(span);
        verifyNoMoreInteractions(observer1, observer2);

        Tracer.unsubscribe(observer1);
        span = startAndCompleteSpan();
        verify(observer2).consume(span);
        verifyNoMoreInteractions(observer1, observer2);

        Tracer.unsubscribe(observer2);
        startAndCompleteSpan();
        verifyNoMoreInteractions(observer1, observer2);
    }

    @Test
    public void testSubscribingIsIdempontent() throws Exception {
        Tracer.subscribe(observer1);
        Tracer.subscribe(observer1);
        Span span = startAndCompleteSpan();
        verify(observer1, times(1)).consume(span);
        verifyNoMoreInteractions(observer1, observer2);
    }

    @Test
    public void testDoesNotNotifyObserversWhenCompletingNonexistingSpan() throws Exception {
        Tracer.subscribe(observer1);
        Tracer.subscribe(observer2);
        Tracer.completeSpan(); // no active span.
        verifyNoMoreInteractions(observer1, observer2);
    }

    @Test
    public void testObserversAreInvokedOnObservableTracesOnly() throws Exception {
        Tracer.subscribe(observer1);

        Tracer.initTrace(Optional.of(true), Tracers.randomId());
        Span span = startAndCompleteSpan();
        verify(observer1).consume(span);
        span = startAndCompleteSpan();
        verify(observer1).consume(span);
        verifyNoMoreInteractions(observer1);

        Tracer.initTrace(Optional.of(false), Tracers.randomId());
        startAndCompleteSpan(); // not sampled, see above
        verifyNoMoreInteractions(observer1);
    }

    @Test
    public void testDerivesNewSpansWhenTraceIsNotObservable() throws Exception {
        Tracer.initTrace(Optional.of(false), Tracers.randomId());
        Tracer.startSpan("foo");
        Tracer.startSpan("bar");
        assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("bar");
        assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("foo");
    }

    @Test
    public void testInitTraceCallsSampler() throws Exception {
        Tracer.setSampler(sampler);
        when(sampler.sample()).thenReturn(true, false);
        Tracer.subscribe(observer1);

        Tracer.initTrace(Optional.<Boolean>absent(), Tracers.randomId());
        verify(sampler).sample();
        Span span = startAndCompleteSpan();
        verify(observer1).consume(span);
        verifyNoMoreInteractions(observer1, sampler);

        Mockito.reset(observer1, sampler);
        Tracer.initTrace(Optional.<Boolean>absent(), Tracers.randomId());
        verify(sampler).sample();
        startAndCompleteSpan(); // not sampled, see above
        verifyNoMoreInteractions(observer1, sampler);
    }

    @Test
    public void testTraceCopyIsIndependent() throws Exception {
        Trace trace = Tracer.copyTrace();
        trace.push(mock(OpenSpan.class));
        assertThat(Tracer.completeSpan().isPresent()).isFalse();
    }

    @Test
    public void testSetTraceSetsCurrentTrace() throws Exception {
        Tracer.startSpan("operation");
        Tracer.setTrace(new Trace(true, "newTraceId"));
        assertThat(Tracer.getTraceId()).isEqualTo("newTraceId");
        assertThat(Tracer.completeSpan().isPresent()).isFalse();
    }

    private static Span startAndCompleteSpan() {
        Tracer.startSpan("operation");
        return Tracer.completeSpan().get();
    }
}
