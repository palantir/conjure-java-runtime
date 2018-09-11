/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.remoting3.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.palantir.remoting.api.tracing.Span;
import com.palantir.remoting.api.tracing.SpanObserver;
import com.palantir.remoting.api.tracing.SpanType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class CloseableTracerTest {

    @Mock
    SpanObserver spanObserver;

    @Captor
    ArgumentCaptor<Span> captor;

    @Before
    public void before() {
        Tracer.getAndClearTrace();
        Tracer.setSampler(AlwaysSampler.INSTANCE);
        Tracer.subscribe("foo", spanObserver);
    }

    @Test
    public void startsAndClosesSpan() {
        try (CloseableTracer tracer = CloseableTracer.startSpan("foo")) {
            // do some work
        }

        verify(spanObserver, times(1)).consume(captor.capture());
        Span span = captor.getValue();
        assertThat(span.getOperation()).isEqualTo("foo");
        assertThat(span.type()).isEqualTo(SpanType.LOCAL);
    }
}
