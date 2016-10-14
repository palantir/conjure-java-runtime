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

package com.palantir.remoting1.tracing.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.palantir.remoting1.tracing.OpenSpan;
import com.palantir.remoting1.tracing.TraceHttpHeaders;
import com.palantir.remoting1.tracing.Tracer;
import com.palantir.remoting1.tracing.Traces;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class OkhttpTraceInterceptorTest {
    @Mock
    private Interceptor.Chain chain;
    private Request request;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        request = new Request.Builder().url("http://localhost").build();
        when(chain.request()).thenReturn(request);
    }

    @After
    public void after() {
        Tracer.initTrace(Optional.of(true), Traces.randomId());
    }

    @Test
    public void testPopulatesNewTrace_whenNoTraceIsPresentInGlobalState() throws IOException {
        OkhttpTraceInterceptor.INSTANCE.intercept(chain);
        verify(chain).request();
        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        verify(chain).proceed(argument.capture());
        verifyNoMoreInteractions(chain);

        Request intercepted = argument.getValue();
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
        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        verify(chain).proceed(argument.capture());
        verifyNoMoreInteractions(chain);

        Request intercepted = argument.getValue();
        assertThat(intercepted.header(TraceHttpHeaders.SPAN_ID)).isNotNull();
        assertThat(intercepted.header(TraceHttpHeaders.SPAN_ID)).isNotEqualTo(parentState.getSpanId());
        assertThat(intercepted.header(TraceHttpHeaders.TRACE_ID)).isEqualTo(traceId);
        assertThat(intercepted.header(TraceHttpHeaders.PARENT_SPAN_ID)).isEqualTo(parentState.getSpanId());
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
}
