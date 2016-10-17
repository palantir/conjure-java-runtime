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

import com.google.common.collect.Lists;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public final class TracesTest {

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testExecutorServiceWrapsCallables() throws Exception {
        ExecutorService wrappedService = Traces.wrap(Executors.newSingleThreadExecutor());

        // Empty trace
        wrappedService.submit(traceExpectingCallable()).get();
        wrappedService.submit(traceExpectingRunnable()).get();

        // Non-empty trace
        Tracer.startSpan("foo");
        Tracer.startSpan("bar");
        Tracer.startSpan("baz");
        wrappedService.submit(traceExpectingCallable()).get();
        wrappedService.submit(traceExpectingRunnable()).get();
        Tracer.completeSpan();
        Tracer.completeSpan();
        Tracer.completeSpan();
    }

    @Test
    public void testScheduledExecutorServiceWrapsCallables() throws Exception {
        ScheduledExecutorService wrappedService = Traces.wrap(Executors.newSingleThreadScheduledExecutor());

        // Empty trace
        wrappedService.schedule(traceExpectingCallable(), 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(traceExpectingRunnable(), 0, TimeUnit.SECONDS).get();

        // Non-empty trace
        Tracer.startSpan("foo");
        Tracer.startSpan("bar");
        Tracer.startSpan("baz");
        wrappedService.schedule(traceExpectingCallable(), 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(traceExpectingRunnable(), 0, TimeUnit.SECONDS).get();
        Tracer.completeSpan();
        Tracer.completeSpan();
        Tracer.completeSpan();
    }

    @Test
    public void testWrappingRunnable_runnableTraceIsIsolated() throws Exception {
        Tracer.startSpan("outside");
        Runnable runnable = Traces.wrap(new Runnable() {
            @Override
            public void run() {
                Tracer.startSpan("inside"); // never completed
            }
        });
        runnable.run();
        assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("outside");
    }

    @Test
    public void testWrappingRunnable_traceStateIsCapturedAtConstructionTime() throws Exception {
        Tracer.startSpan("before-construction");
        Runnable runnable = Traces.wrap(new Runnable() {
            @Override
            public void run() {
                assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("before-construction");
            }
        });
        Tracer.startSpan("after-construction");
        runnable.run();
    }

    @Test
    public void testWrappingCallable_runnableTraceIsIsolated() throws Exception {
        Tracer.startSpan("outside");
        Callable<Void> runnable = Traces.wrap(new Callable<Void>() {
            @Override
            public Void call() {
                Tracer.startSpan("inside"); // never completed
                return null;
            }
        });
        runnable.call();
        assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("outside");
    }

    @Test
    public void testWrappingCallable_traceStateIsCapturedAtConstructionTime() throws Exception {
        Tracer.startSpan("before-construction");
        Callable<Void> runnable = Traces.wrap(new Callable<Void>() {
            @Override
            public Void call() {
                assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("before-construction");
                return null;
            }
        });
        Tracer.startSpan("after-construction");
        runnable.call();
    }

    private static Callable<Void> traceExpectingCallable() {
        final String expectedTraceId = Tracer.getTraceId();
        final List<OpenSpan> expectedTrace = getCurrentFullTrace();
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                assertThat(Tracer.getTraceId()).isEqualTo(expectedTraceId);
                assertThat(getCurrentFullTrace()).isEqualTo(expectedTrace);
                return null;
            }
        };
    }

    private static Runnable traceExpectingRunnable() {
        final String expectedTraceId = Tracer.getTraceId();
        final List<OpenSpan> expectedTrace = getCurrentFullTrace();
        return new Runnable() {
            @Override
            public void run() {
                assertThat(Tracer.getTraceId()).isEqualTo(expectedTraceId);
                assertThat(getCurrentFullTrace()).isEqualTo(expectedTrace);
            }
        };
    }

    private static List<OpenSpan> getCurrentFullTrace() {
        Trace trace = Tracer.copyTrace();
        List<OpenSpan> spans = Lists.newArrayList();
        while (!trace.isEmpty()) {
            spans.add(trace.pop().get());
        }
        return spans;
    }
}
