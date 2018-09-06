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

package com.palantir.remoting3.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Lists;
import com.palantir.remoting.api.tracing.OpenSpan;
import com.palantir.tracing.ExposedTracer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;

public final class TracersTest {

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        MDC.clear();
    }

    @Test
    public void testExecutorServiceWrapsCallables() throws Exception {
        ExecutorService wrappedService = com.palantir.remoting3.tracing.Tracers.wrap(Executors.newSingleThreadExecutor());

        // Empty trace
        wrappedService.submit(traceExpectingCallable()).get();
        wrappedService.submit(traceExpectingRunnable()).get();

        // Non-empty trace
        com.palantir.remoting3.tracing.Tracer.startSpan("foo");
        com.palantir.remoting3.tracing.Tracer.startSpan("bar");
        com.palantir.remoting3.tracing.Tracer.startSpan("baz");
        wrappedService.submit(traceExpectingCallable()).get();
        wrappedService.submit(traceExpectingRunnable()).get();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
    }

    @Test
    public void testScheduledExecutorServiceWrapsCallables() throws Exception {
        ScheduledExecutorService wrappedService = com.palantir.remoting3.tracing.Tracers.wrap(Executors.newSingleThreadScheduledExecutor());

        // Empty trace
        wrappedService.schedule(traceExpectingCallable(), 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(traceExpectingRunnable(), 0, TimeUnit.SECONDS).get();

        // Non-empty trace
        com.palantir.remoting3.tracing.Tracer.startSpan("foo");
        com.palantir.remoting3.tracing.Tracer.startSpan("bar");
        com.palantir.remoting3.tracing.Tracer.startSpan("baz");
        wrappedService.schedule(traceExpectingCallable(), 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(traceExpectingRunnable(), 0, TimeUnit.SECONDS).get();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
    }

    @Test
    public void testScheduledExecutorServiceWrapsCallablesWithNewTraces() throws Exception {
        ScheduledExecutorService wrappedService =
                com.palantir.remoting3.tracing.Tracers.wrapWithNewTrace(Executors.newSingleThreadScheduledExecutor());

        Callable<Void> callable = newTraceExpectingCallable();
        Runnable runnable = newTraceExpectingRunnable();

        // Empty trace
        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();

        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();

        // Non-empty trace
        com.palantir.remoting3.tracing.Tracer.startSpan("foo");
        com.palantir.remoting3.tracing.Tracer.startSpan("bar");
        com.palantir.remoting3.tracing.Tracer.startSpan("baz");
        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
    }

    @Test
    public void testExecutorServiceWrapsCallablesWithNewTraces() throws Exception {
        ExecutorService wrappedService =
                com.palantir.remoting3.tracing.Tracers.wrapWithNewTrace(Executors.newSingleThreadExecutor());

        Callable<Void> callable = newTraceExpectingCallable();
        Runnable runnable = newTraceExpectingRunnable();

        // Empty trace
        wrappedService.submit(callable).get();
        wrappedService.submit(runnable).get();

        wrappedService.submit(callable).get();
        wrappedService.submit(runnable).get();

        // Non-empty trace
        com.palantir.remoting3.tracing.Tracer.startSpan("foo");
        com.palantir.remoting3.tracing.Tracer.startSpan("bar");
        com.palantir.remoting3.tracing.Tracer.startSpan("baz");
        wrappedService.submit(callable).get();
        wrappedService.submit(runnable).get();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
        com.palantir.remoting3.tracing.Tracer.completeSpan();
    }

    @Test
    public void testWrappingRunnable_runnableTraceIsIsolated() throws Exception {
        com.palantir.remoting3.tracing.Tracer.startSpan("outside");
        Runnable runnable = com.palantir.remoting3.tracing.Tracers.wrap(new Runnable() {
            @Override
            public void run() {
                com.palantir.remoting3.tracing.Tracer.startSpan("inside"); // never completed
            }
        });
        runnable.run();
        assertThat(com.palantir.remoting3.tracing.Tracer.completeSpan().get().getOperation()).isEqualTo("outside");
    }

    @Test
    public void testWrappingRunnable_traceStateIsCapturedAtConstructionTime() throws Exception {
        com.palantir.remoting3.tracing.Tracer.startSpan("before-construction");
        Runnable runnable = com.palantir.remoting3.tracing.Tracers.wrap(new Runnable() {
            @Override
            public void run() {
                assertThat(com.palantir.remoting3.tracing.Tracer.completeSpan().get().getOperation()).isEqualTo("before-construction");
            }
        });
        com.palantir.remoting3.tracing.Tracer.startSpan("after-construction");
        runnable.run();
    }

    @Test
    public void testWrappingCallable_callableTraceIsIsolated() throws Exception {
        com.palantir.remoting3.tracing.Tracer.startSpan("outside");
        Callable<Void> runnable = com.palantir.remoting3.tracing.Tracers.wrap(new Callable<Void>() {
            @Override
            public Void call() {
                com.palantir.remoting3.tracing.Tracer.startSpan("inside"); // never completed
                return null;
            }
        });
        runnable.call();
        assertThat(com.palantir.remoting3.tracing.Tracer.completeSpan().get().getOperation()).isEqualTo("outside");
    }

    @Test
    public void testWrappingCallable_traceStateIsCapturedAtConstructionTime() throws Exception {
        com.palantir.remoting3.tracing.Tracer.startSpan("before-construction");
        Callable<Void> callable = com.palantir.remoting3.tracing.Tracers.wrap(new Callable<Void>() {
            @Override
            public Void call() {
                assertThat(com.palantir.remoting3.tracing.Tracer.completeSpan().get().getOperation()).isEqualTo("before-construction");
                return null;
            }
        });
        com.palantir.remoting3.tracing.Tracer.startSpan("after-construction");
        callable.call();
    }

    @Test
    public void testWrapCallableWithNewTrace_traceStateInsideCallableIsIsolated() throws Exception {
        String traceIdBeforeConstruction = com.palantir.remoting3.tracing.Tracer.getTraceId();

        Callable<String> wrappedCallable = com.palantir.remoting3.tracing.Tracers.wrapWithNewTrace(() -> {
            return com.palantir.remoting3.tracing.Tracer.getTraceId();
        });

        String traceIdFirstCall = wrappedCallable.call();
        String traceIdSecondCall = wrappedCallable.call();

        String traceIdAfterCalls = com.palantir.remoting3.tracing.Tracer.getTraceId();

        assertThat(traceIdFirstCall)
                .isNotEqualTo(traceIdBeforeConstruction)
                .isNotEqualTo(traceIdAfterCalls)
                .isNotEqualTo(traceIdSecondCall);

        assertThat(traceIdSecondCall)
                .isNotEqualTo(traceIdBeforeConstruction)
                .isNotEqualTo(traceIdAfterCalls);

        assertThat(traceIdBeforeConstruction)
                .isEqualTo(traceIdAfterCalls);
    }

    @Test
    public void testWrapCallableWithNewTrace_traceStateRestoredWhenThrows() throws Exception {
        String traceIdBeforeConstruction = com.palantir.remoting3.tracing.Tracer.getTraceId();

        Callable<String> wrappedCallable = com.palantir.remoting3.tracing.Tracers.wrapWithNewTrace(() -> {
            throw new IllegalStateException();
        });

        assertThatThrownBy(() -> wrappedCallable.call()).isInstanceOf(IllegalStateException.class);

        assertThat(com.palantir.remoting3.tracing.Tracer.getTraceId()).isEqualTo(traceIdBeforeConstruction);
    }

    @Test
    public void testWrapRunnableWithNewTrace_traceStateInsideRunnableIsIsolated() throws Exception {
        String traceIdBeforeConstruction = com.palantir.remoting3.tracing.Tracer.getTraceId();

        List<String> traceIds = Lists.newArrayList();

        Runnable wrappedRunnable = com.palantir.remoting3.tracing.Tracers.wrapWithNewTrace(() -> {
            traceIds.add(com.palantir.remoting3.tracing.Tracer.getTraceId());
        });

        wrappedRunnable.run();
        wrappedRunnable.run();

        String traceIdFirstCall = traceIds.get(0);
        String traceIdSecondCall = traceIds.get(1);

        String traceIdAfterCalls = com.palantir.remoting3.tracing.Tracer.getTraceId();

        assertThat(traceIdFirstCall)
                .isNotEqualTo(traceIdBeforeConstruction)
                .isNotEqualTo(traceIdAfterCalls)
                .isNotEqualTo(traceIdSecondCall);

        assertThat(traceIdSecondCall)
                .isNotEqualTo(traceIdBeforeConstruction)
                .isNotEqualTo(traceIdAfterCalls);

        assertThat(traceIdBeforeConstruction)
                .isEqualTo(traceIdAfterCalls);
    }

    @Test
    public void testWrapRunnableWithNewTrace_traceStateRestoredWhenThrows() throws Exception {
        String traceIdBeforeConstruction = com.palantir.remoting3.tracing.Tracer.getTraceId();

        Runnable rawRunnable = () -> {
            throw new IllegalStateException();
        };
        Runnable wrappedRunnable = com.palantir.remoting3.tracing.Tracers.wrapWithNewTrace(rawRunnable);

        assertThatThrownBy(() -> wrappedRunnable.run()).isInstanceOf(IllegalStateException.class);

        assertThat(com.palantir.remoting3.tracing.Tracer.getTraceId()).isEqualTo(traceIdBeforeConstruction);
    }

    @Test
    public void testWrapRunnableWithAlternateTraceId_traceStateInsideRunnableUsesGivenTraceId() {
        String traceIdBeforeConstruction = com.palantir.remoting3.tracing.Tracer.getTraceId();
        AtomicReference<String> traceId = new AtomicReference<>();
        String traceIdToUse = "someTraceId";
        Runnable wrappedRunnable = com.palantir.remoting3.tracing.Tracers.wrapWithAlternateTraceId(traceIdToUse, () -> {
            traceId.set(com.palantir.remoting3.tracing.Tracer.getTraceId());
        });

        wrappedRunnable.run();

        String traceIdAfterCall = com.palantir.remoting3.tracing.Tracer.getTraceId();

        assertThat(traceId.get())
                .isNotEqualTo(traceIdBeforeConstruction)
                .isNotEqualTo(traceIdAfterCall)
                .isEqualTo(traceIdToUse);

        assertThat(traceIdBeforeConstruction).isEqualTo(traceIdAfterCall);
    }

    @Test
    public void testWrapRunnableWithAlternateTraceId_traceStateRestoredWhenThrows() {
        String traceIdBeforeConstruction = com.palantir.remoting3.tracing.Tracer.getTraceId();
        Runnable rawRunnable = () -> {
            throw new IllegalStateException();
        };
        Runnable wrappedRunnable = com.palantir.remoting3.tracing.Tracers.wrapWithAlternateTraceId("someTraceId", rawRunnable);

        assertThatThrownBy(() -> wrappedRunnable.run()).isInstanceOf(IllegalStateException.class);
        assertThat(com.palantir.remoting3.tracing.Tracer.getTraceId()).isEqualTo(traceIdBeforeConstruction);
    }

    @Test
    public void testTraceIdGeneration() throws Exception {
        assertThat(com.palantir.remoting3.tracing.Tracers.randomId()).hasSize(16); // fails with p=1/16 if generated string is not padded
        assertThat(com.palantir.remoting3.tracing.Tracers.longToPaddedHex(0)).isEqualTo("0000000000000000");
        assertThat(com.palantir.remoting3.tracing.Tracers.longToPaddedHex(42)).isEqualTo("000000000000002a");
        assertThat(com.palantir.remoting3.tracing.Tracers.longToPaddedHex(-42)).isEqualTo("ffffffffffffffd6");
        assertThat(com.palantir.remoting3.tracing.Tracers.longToPaddedHex(123456789L)).isEqualTo("00000000075bcd15");
    }

    private static Callable<Void> newTraceExpectingCallable() {
        final Set<String> seenTraceIds = new HashSet<>();
        seenTraceIds.add(com.palantir.remoting3.tracing.Tracer.getTraceId());

        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                String newTraceId = com.palantir.remoting3.tracing.Tracer.getTraceId();

                assertThat(MDC.get(com.palantir.remoting3.tracing.Tracers.TRACE_ID_KEY)).isEqualTo(newTraceId);
                assertThat(seenTraceIds).doesNotContain(newTraceId);
                seenTraceIds.add(newTraceId);
                return null;
            }
        };
    }

    private static Runnable newTraceExpectingRunnable() {
        final Set<String> seenTraceIds = new HashSet<>();
        seenTraceIds.add(com.palantir.remoting3.tracing.Tracer.getTraceId());

        return new Runnable() {
            @Override
            public void run() {
                String newTraceId = com.palantir.remoting3.tracing.Tracer.getTraceId();

                assertThat(MDC.get(com.palantir.remoting3.tracing.Tracers.TRACE_ID_KEY)).isEqualTo(newTraceId);
                assertThat(seenTraceIds).doesNotContain(newTraceId);
                seenTraceIds.add(newTraceId);
            }
        };
    }

    private static Callable<Void> traceExpectingCallable() {
        final String expectedTraceId = com.palantir.remoting3.tracing.Tracer.getTraceId();
        final List<OpenSpan> expectedTrace = getCurrentFullTrace();
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                assertThat(com.palantir.remoting3.tracing.Tracer.getTraceId()).isEqualTo(expectedTraceId);
                assertThat(getCurrentFullTrace()).isEqualTo(expectedTrace);
                assertThat(MDC.get(com.palantir.remoting3.tracing.Tracers.TRACE_ID_KEY)).isEqualTo(expectedTraceId);
                return null;
            }
        };
    }

    private static Runnable traceExpectingRunnable() {
        final String expectedTraceId = com.palantir.remoting3.tracing.Tracer.getTraceId();
        final List<OpenSpan> expectedTrace = getCurrentFullTrace();
        return new Runnable() {
            @Override
            public void run() {
                assertThat(com.palantir.remoting3.tracing.Tracer.getTraceId()).isEqualTo(expectedTraceId);
                assertThat(getCurrentFullTrace()).isEqualTo(expectedTrace);
                assertThat(MDC.get(Tracers.TRACE_ID_KEY)).isEqualTo(expectedTraceId);
            }
        };
    }

    private static List<OpenSpan> getCurrentFullTrace() {
        Trace trace = ExposedTracer.copyTrace();
        List<OpenSpan> spans = Lists.newArrayList();
        while (!trace.isEmpty()) {
            spans.add(trace.pop().get());
        }
        return spans;
    }
}
