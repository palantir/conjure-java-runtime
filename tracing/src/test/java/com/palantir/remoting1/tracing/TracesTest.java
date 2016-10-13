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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class TracesTest {

    @Mock
    private Traces.SpanObserver observer1;
    @Mock
    private Traces.SpanObserver observer2;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSubscribeUnsubscribe() throws Exception {
        // no error when completing span without a registered subscriber
        startAndCompleteSpan();

        Traces.subscribe(observer1);
        Traces.subscribe(observer2);
        Span span = startAndCompleteSpan();
        verify(observer1).consume(span);
        verify(observer2).consume(span);
        verifyNoMoreInteractions(observer1, observer2);

        Traces.unsubscribe(observer1);
        span = startAndCompleteSpan();
        verify(observer2).consume(span);
        verifyNoMoreInteractions(observer1, observer2);

        Traces.unsubscribe(observer2);
        startAndCompleteSpan();
        verifyNoMoreInteractions(observer1, observer2);
    }

    @Test
    public void testSubscribingIsIdempontent() throws Exception {
        Traces.subscribe(observer1);
        Traces.subscribe(observer1);
        Span span = startAndCompleteSpan();
        verify(observer1, times(1)).consume(span);
        verifyNoMoreInteractions(observer1, observer2);
    }

    @Test
    public void testDoesNotNotifyObserversWhenCompletingNonexistingSpan() throws Exception {
        Traces.subscribe(observer1);
        Traces.subscribe(observer2);
        Traces.completeSpan(); // no active span.
        verifyNoMoreInteractions(observer1, observer2);
    }

    private static Span startAndCompleteSpan() {
        Traces.startSpan("operation");
        return Traces.completeSpan().get();
    }
}
