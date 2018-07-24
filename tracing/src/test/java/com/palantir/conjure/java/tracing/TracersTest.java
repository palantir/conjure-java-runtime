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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.Lists;
import com.palantir.conjure.java.api.tracing.OpenSpan;
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
        ExecutorService wrappedService = Tracers.wrap(Executors.newSingleThreadExecutor());

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
        ScheduledExecutorService wrappedService = Tracers.wrap(Executors.newSingleThreadScheduledExecutor());

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
    public void testScheduledExecutorServiceWrapsCallablesWithNewTraces() throws Exception {
        ScheduledExecutorService wrappedService =
                Tracers.wrapWithNewTrace(Executors.newSingleThreadScheduledExecutor());

        Callable<Void> callable = newTraceExpectingCallable();
        Runnable runnable = newTraceExpectingRunnable();

        // Empty trace
        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();

        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();

        // Non-empty trace
        Tracer.startSpan("foo");
        Tracer.startSpan("bar");
        Tracer.startSpan("baz");
        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();
        Tracer.completeSpan();
        Tracer.completeSpan();
        Tracer.completeSpan();
    }

    @Test
    public void testExecutorServiceWrapsCallablesWithNewTraces() throws Exception {
        ExecutorService wrappedService =
                Tracers.wrapWithNewTrace(Executors.newSingleThreadExecutor());

        Callable<Void> callable = newTraceExpectingCallable();
        Runnable runnable = newTraceExpectingRunnable();

        // Empty trace
        wrappedService.submit(callable).get();
        wrappedService.submit(runnable).get();

        wrappedService.submit(callable).get();
        wrappedService.submit(runnable).get();

        // Non-empty trace
        Tracer.startSpan("foo");
        Tracer.startSpan("bar");
        Tracer.startSpan("baz");
        wrappedService.submit(callable).get();
        wrappedService.submit(runnable).get();
        Tracer.completeSpan();
        Tracer.completeSpan();
        Tracer.completeSpan();
    }

    @Test
    public void testWrappingRunnable_runnableTraceIsIsolated() throws Exception {
        Tracer.startSpan("outside");
        Runnable runnable = Tracers.wrap(new Runnable() {
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
        Runnable runnable = Tracers.wrap(new Runnable() {
            @Override
            public void run() {
                assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("before-construction");
            }
        });
        Tracer.startSpan("after-construction");
        runnable.run();
    }

    @Test
    public void testWrappingCallable_callableTraceIsIsolated() throws Exception {
        Tracer.startSpan("outside");
        Callable<Void> runnable = Tracers.wrap(new Callable<Void>() {
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
        Callable<Void> callable = Tracers.wrap(new Callable<Void>() {
            @Override
            public Void call() {
                assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("before-construction");
                return null;
            }
        });
        Tracer.startSpan("after-construction");
        callable.call();
    }

    @Test
    public void testWrapCallableWithNewTrace_traceStateInsideCallableIsIsolated() throws Exception {
        String traceIdBeforeConstruction = Tracer.getTraceId();

        Callable<String> wrappedCallable = Tracers.wrapWithNewTrace(() -> {
            return Tracer.getTraceId();
        });

        String traceIdFirstCall = wrappedCallable.call();
        String traceIdSecondCall = wrappedCallable.call();

        String traceIdAfterCalls = Tracer.getTraceId();

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
        String traceIdBeforeConstruction = Tracer.getTraceId();

        Callable<String> wrappedCallable = Tracers.wrapWithNewTrace(() -> {
            throw new IllegalStateException();
        });

        assertThatThrownBy(() -> wrappedCallable.call()).isInstanceOf(IllegalStateException.class);

        assertThat(Tracer.getTraceId()).isEqualTo(traceIdBeforeConstruction);
    }

    @Test
    public void testWrapRunnableWithNewTrace_traceStateInsideRunnableIsIsolated() throws Exception {
        String traceIdBeforeConstruction = Tracer.getTraceId();

        List<String> traceIds = Lists.newArrayList();

        Runnable wrappedRunnable = Tracers.wrapWithNewTrace(() -> {
            traceIds.add(Tracer.getTraceId());
        });

        wrappedRunnable.run();
        wrappedRunnable.run();

        String traceIdFirstCall = traceIds.get(0);
        String traceIdSecondCall = traceIds.get(1);

        String traceIdAfterCalls = Tracer.getTraceId();

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
        String traceIdBeforeConstruction = Tracer.getTraceId();

        Runnable rawRunnable = () -> {
            throw new IllegalStateException();
        };
        Runnable wrappedRunnable = Tracers.wrapWithNewTrace(rawRunnable);

        assertThatThrownBy(() -> wrappedRunnable.run()).isInstanceOf(IllegalStateException.class);

        assertThat(Tracer.getTraceId()).isEqualTo(traceIdBeforeConstruction);
    }

    @Test
    public void testWrapRunnableWithAlternateTraceId_traceStateInsideRunnableUsesGivenTraceId() {
        String traceIdBeforeConstruction = Tracer.getTraceId();
        AtomicReference<String> traceId = new AtomicReference<>();
        String traceIdToUse = "someTraceId";
        Runnable wrappedRunnable = Tracers.wrapWithAlternateTraceId(traceIdToUse, () -> {
            traceId.set(Tracer.getTraceId());
        });

        wrappedRunnable.run();

        String traceIdAfterCall = Tracer.getTraceId();

        assertThat(traceId.get())
                .isNotEqualTo(traceIdBeforeConstruction)
                .isNotEqualTo(traceIdAfterCall)
                .isEqualTo(traceIdToUse);

        assertThat(traceIdBeforeConstruction).isEqualTo(traceIdAfterCall);
    }

    @Test
    public void testWrapRunnableWithAlternateTraceId_traceStateRestoredWhenThrows() {
        String traceIdBeforeConstruction = Tracer.getTraceId();
        Runnable rawRunnable = () -> {
            throw new IllegalStateException();
        };
        Runnable wrappedRunnable = Tracers.wrapWithAlternateTraceId("someTraceId", rawRunnable);

        assertThatThrownBy(() -> wrappedRunnable.run()).isInstanceOf(IllegalStateException.class);
        assertThat(Tracer.getTraceId()).isEqualTo(traceIdBeforeConstruction);
    }

    @Test
    public void testTraceIdGeneration() throws Exception {
        assertThat(Tracers.randomId()).hasSize(16); // fails with p=1/16 if generated string is not padded
        assertThat(Tracers.longToPaddedHex(0)).isEqualTo("0000000000000000");
        assertThat(Tracers.longToPaddedHex(42)).isEqualTo("000000000000002a");
        assertThat(Tracers.longToPaddedHex(-42)).isEqualTo("ffffffffffffffd6");
        assertThat(Tracers.longToPaddedHex(123456789L)).isEqualTo("00000000075bcd15");
    }

    private static Callable<Void> newTraceExpectingCallable() {
        final Set<String> seenTraceIds = new HashSet<>();
        seenTraceIds.add(Tracer.getTraceId());

        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                String newTraceId = Tracer.getTraceId();

                assertThat(MDC.get(Tracers.TRACE_ID_KEY)).isEqualTo(newTraceId);
                assertThat(seenTraceIds).doesNotContain(newTraceId);
                seenTraceIds.add(newTraceId);
                return null;
            }
        };
    }

    private static Runnable newTraceExpectingRunnable() {
        final Set<String> seenTraceIds = new HashSet<>();
        seenTraceIds.add(Tracer.getTraceId());

        return new Runnable() {
            @Override
            public void run() {
                String newTraceId = Tracer.getTraceId();

                assertThat(MDC.get(Tracers.TRACE_ID_KEY)).isEqualTo(newTraceId);
                assertThat(seenTraceIds).doesNotContain(newTraceId);
                seenTraceIds.add(newTraceId);
            }
        };
    }

    private static Callable<Void> traceExpectingCallable() {
        final String expectedTraceId = Tracer.getTraceId();
        final List<OpenSpan> expectedTrace = getCurrentFullTrace();
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                assertThat(Tracer.getTraceId()).isEqualTo(expectedTraceId);
                assertThat(getCurrentFullTrace()).isEqualTo(expectedTrace);
                assertThat(MDC.get(Tracers.TRACE_ID_KEY)).isEqualTo(expectedTraceId);
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
                assertThat(MDC.get(Tracers.TRACE_ID_KEY)).isEqualTo(expectedTraceId);
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
