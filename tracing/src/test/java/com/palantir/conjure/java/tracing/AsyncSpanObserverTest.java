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

package com.palantir.conjure.java.tracing;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.palantir.conjure.java.api.tracing.Span;
import com.palantir.conjure.java.api.tracing.SpanObserver;
import com.palantir.conjure.java.api.tracing.SpanType;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class AsyncSpanObserverTest {

    @Mock
    private SpanObserver observer;

    private DeterministicScheduler scheduler;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        scheduler = new DeterministicScheduler();
    }

    @Test
    public void testDropsExcessRequests() throws Exception {
        SpanObserver asyncObserver = createAsyncObserver(2);
        asyncObserver.consume(span(1));
        asyncObserver.consume(span(2));
        asyncObserver.consume(span(3)); // gets bumped

        verify(observer, never()).consume(any(Span.class));
        scheduler.runNextPendingCommand();
        verify(observer).consume(span(1));
        scheduler.runNextPendingCommand();
        verify(observer).consume(span(2));
        scheduler.runUntilIdle();
        verifyNoMoreInteractions(observer);
    }

    @Test
    public void testSchedulesRequestsOnceBelowThreshold() throws Exception {
        SpanObserver asyncObserver = createAsyncObserver(2);
        asyncObserver.consume(span(1));
        asyncObserver.consume(span(2));
        scheduler.runNextPendingCommand(); // evicts span(1)
        verify(observer).consume(span(1));

        asyncObserver.consume(span(3));
        scheduler.runNextPendingCommand();
        verify(observer).consume(span(2));
        scheduler.runNextPendingCommand();
        verify(observer).consume(span(3));
        scheduler.runUntilIdle();
        verifyNoMoreInteractions(observer);
    }

    private SpanObserver createAsyncObserver(int maxInflights) {
        return Observers.asyncDecorator(observer, scheduler, maxInflights);
    }

    private Span span(int id) {
        return Span.builder()
                .traceId(Integer.toString(id))
                .spanId("")
                .type(SpanType.LOCAL)
                .operation("")
                .startTimeMicroSeconds(0)
                .durationNanoSeconds(0)
                .build();
    }
}
