/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.tracing.okhttp3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.palantir.conjure.java.api.tracing.OpenSpan;
import com.palantir.conjure.java.api.tracing.Span;
import com.palantir.conjure.java.api.tracing.SpanObserver;
import com.palantir.conjure.java.api.tracing.SpanType;
import com.palantir.conjure.java.api.tracing.TraceHttpHeaders;
import com.palantir.conjure.java.tracing.Tracer;
import com.palantir.conjure.java.tracing.Tracers;
import java.io.IOException;
import java.util.Optional;
import okhttp3.Interceptor;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class OkhttpTraceInterceptorTest {

    @Mock
    private Interceptor.Chain chain;

    @Mock
    private SpanObserver observer;

    @Captor
    private ArgumentCaptor<Request> requestCaptor;

    @Captor
    private ArgumentCaptor<Span> spanCaptor;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        Request request = new Request.Builder().url("http://localhost").build();
        when(chain.request()).thenReturn(request);
        Tracer.subscribe("", observer);
    }

    @After
    public void after() {
        Tracer.initTrace(Optional.of(true), Tracers.randomId());
        Tracer.unsubscribe("");
    }

    @Test
    public void testPopulatesNewTrace_whenNoTraceIsPresentInGlobalState() throws IOException {
        OkhttpTraceInterceptor.INSTANCE.intercept(chain);
        verify(chain).request();
        verify(chain).proceed(requestCaptor.capture());
        verifyNoMoreInteractions(chain);

        Request intercepted = requestCaptor.getValue();
        assertThat(intercepted.header(TraceHttpHeaders.SPAN_ID)).isNotNull();
        assertThat(intercepted.header(TraceHttpHeaders.TRACE_ID)).isNotNull();
        assertThat(intercepted.header(TraceHttpHeaders.PARENT_SPAN_ID)).isNull();
    }

    @Test
    public void testPopulatesNewTrace_whenParentTraceIsPresent() throws IOException {
        OpenSpan parentState = Tracer.startSpan("operation");
        String traceId = Tracer.getTraceId();
        try {
            OkhttpTraceInterceptor.INSTANCE.intercept(chain);
        } finally {
            Tracer.completeSpan();
        }

        verify(chain).request();
        verify(chain).proceed(requestCaptor.capture());
        verifyNoMoreInteractions(chain);

        Request intercepted = requestCaptor.getValue();
        assertThat(intercepted.header(TraceHttpHeaders.SPAN_ID)).isNotNull();
        assertThat(intercepted.header(TraceHttpHeaders.SPAN_ID)).isNotEqualTo(parentState.getSpanId());
        assertThat(intercepted.header(TraceHttpHeaders.TRACE_ID)).isEqualTo(traceId);
        assertThat(intercepted.header(TraceHttpHeaders.PARENT_SPAN_ID)).isEqualTo(parentState.getSpanId());
    }

    @Test
    public void testAddsIsSampledHeader_whenTraceIsObservable() throws IOException {
        OkhttpTraceInterceptor.INSTANCE.intercept(chain);
        verify(chain).proceed(requestCaptor.capture());
        assertThat(requestCaptor.getValue().header(TraceHttpHeaders.IS_SAMPLED)).isEqualTo("1");
    }

    @Test
    public void testHeaders_whenTraceIsNotObservable() throws IOException {
        Tracer.initTrace(Optional.of(false), Tracers.randomId());
        String traceId = Tracer.getTraceId();
        OkhttpTraceInterceptor.INSTANCE.intercept(chain);
        verify(chain).proceed(requestCaptor.capture());
        Request intercepted = requestCaptor.getValue();
        assertThat(intercepted.header(TraceHttpHeaders.SPAN_ID)).isNotNull();
        assertThat(intercepted.header(TraceHttpHeaders.TRACE_ID)).isEqualTo(traceId);
        assertThat(intercepted.header(TraceHttpHeaders.IS_SAMPLED)).isEqualTo("0");
    }

    @Test
    public void testPopsSpan() throws IOException {
        OpenSpan before = Tracer.startSpan("");
        OkhttpTraceInterceptor.INSTANCE.intercept(chain);
        assertThat(Tracer.startSpan("").getParentSpanId().get()).isEqualTo(before.getSpanId());
    }

    @Test
    public void testPopsSpanEvenWhenChainFails() throws IOException {
        OpenSpan before = Tracer.startSpan("op");
        when(chain.proceed(any(Request.class))).thenThrow(new IllegalStateException());
        try {
            OkhttpTraceInterceptor.INSTANCE.intercept(chain);
        } catch (IllegalStateException e) { /* expected */ }
        assertThat(Tracer.startSpan("").getParentSpanId().get()).isEqualTo(before.getSpanId());
    }

    @Test
    public void testCompletesSpan() throws Exception {
        OpenSpan outerSpan = Tracer.startSpan("outer");
        OkhttpTraceInterceptor.INSTANCE.intercept(chain);
        verify(observer).consume(spanCaptor.capture());
        Span okhttpSpan = spanCaptor.getValue();
        assertThat(okhttpSpan).isNotEqualTo(outerSpan);
    }

    @Test
    public void testSpanHasClientType() throws Exception {
        OkhttpTraceInterceptor.INSTANCE.intercept(chain);
        verify(observer).consume(spanCaptor.capture());
        Span okhttpSpan = spanCaptor.getValue();
        assertThat(okhttpSpan.type()).isEqualTo(SpanType.CLIENT_OUTGOING);
    }
}
