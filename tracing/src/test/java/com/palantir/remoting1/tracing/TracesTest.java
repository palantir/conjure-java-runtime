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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public final class TracesTest {

    @Mock
    Traces.Subscriber subscriber1;
    @Mock
    Traces.Subscriber subscriber2;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSubscribeUnsubscribe() throws Exception {
        // no error when completing span without a registered subscriber
        startAndCompleteSpan();

        Traces.subscribe(subscriber1);
        Traces.subscribe(subscriber2);
        Span span = startAndCompleteSpan();
        verify(subscriber1).consume(span);
        verify(subscriber2).consume(span);
        Mockito.verifyNoMoreInteractions(subscriber1, subscriber2);

        Traces.unsubscribe(subscriber1);
        span = startAndCompleteSpan();
        verify(subscriber2).consume(span);
        Mockito.verifyNoMoreInteractions(subscriber1, subscriber2);

        Traces.unsubscribe(subscriber2);
        startAndCompleteSpan();
        Mockito.verifyNoMoreInteractions(subscriber1, subscriber2);
    }

    @Test
    public void testSubscribingIsIdempontent() throws Exception {
        Traces.subscribe(subscriber1);
        Traces.subscribe(subscriber1);
        Span span = startAndCompleteSpan();
        verify(subscriber1, times(1)).consume(span);
        Mockito.verifyNoMoreInteractions(subscriber1, subscriber2);
    }

    @Test
    public void testDoesNotNotifySubscribersWhenComplitingNonexistingSpan() throws Exception {
        Traces.subscribe(subscriber1);
        Traces.subscribe(subscriber2);
        Traces.completeSpan(); // no active span.
        Mockito.verifyNoMoreInteractions(subscriber1, subscriber2);
    }

    private static Span startAndCompleteSpan() {
        Traces.startSpan("operation");
        return Traces.completeSpan().get();
    }
}