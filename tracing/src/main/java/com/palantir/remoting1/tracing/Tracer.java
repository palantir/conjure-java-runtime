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

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * The singleton entry point for handling Zipkin-style traces and spans. Provides functionality for starting and
 * completing spans, and for subscribing observers to span completion events.
 * <p>
 * This class is thread-safe.
 */
public final class Tracer {

    private Tracer() {}

    // Thread-safe since thread-local
    private static final ThreadLocal<Trace> currentTrace = new ThreadLocal<Trace>() {
        @Override
        protected Trace initialValue() {
            return new Trace(sampler.sample(), Traces.randomId());
        }
    };

    // Thread-safe set implementation
    private static final Set<SpanObserver> observers = Sets.newConcurrentHashSet();

    // Thread-safe since stateless
    private static TraceSampler sampler = AlwaysSampler.INSTANCE;

    /**
     * Initializes the current thread's trace, erasing any previously accrued open spans. The new trace is {@link
     * Trace#isObservable observable} iff the given flag is true, or, iff {@code isObservable} is absent, if the {@link
     * #setSampler configured sampler} returns true.
     */
    public static void initTrace(Optional<Boolean> isObservable, String traceId) {
        validateId(traceId, "traceId must be non-empty: %s");
        boolean observable = isObservable.or(sampler.sample());
        currentTrace.set(new Trace(observable, traceId));
    }

    /**
     * Opens a new span for this thread's call trace, labeled with the provided operation and parent span.
     */
    public static OpenSpan startSpan(String operation, String parentSpanId) {
        Preconditions.checkState(currentTrace.get().isEmpty(),
                "Cannot start a span with explicit parent if the current thread's trace is non-empty");
        validateId(parentSpanId, "parentTraceId must be non-empty: %s");
        OpenSpan span = OpenSpan.builder()
                .spanId(Traces.randomId())
                .operation(operation)
                .parentSpanId(parentSpanId)
                .build();
        currentTrace.get().push(span);
        return span;
    }

    /**
     * Opens a new span for this thread's call trace, labeled with the provided operation.
     */
    public static OpenSpan startSpan(String operation) {
        OpenSpan.Builder spanBuilder = OpenSpan.builder()
                .operation(operation)
                .spanId(Traces.randomId());

        Optional<OpenSpan> prevState = currentTrace.get().top();
        if (prevState.isPresent()) {
            spanBuilder.parentSpanId(prevState.get().getSpanId());
        }

        OpenSpan span = spanBuilder.build();
        currentTrace.get().push(span);
        return span;
    }

    /**
     * Completes and returns the current span (if it exists) and notifies all {@link #observers subscribers} about the
     * completed span.
     */
    public static Optional<Span> completeSpan() {
        Optional<OpenSpan> maybeOpenSpan = currentTrace.get().pop();
        if (!maybeOpenSpan.isPresent()) {
            return Optional.absent();
        } else {
            OpenSpan openSpan = maybeOpenSpan.get();
            Span span = Span.builder()
                    .traceId(getTraceId())
                    .spanId(openSpan.getSpanId())
                    .parentSpanId(openSpan.getParentSpanId())
                    .operation(openSpan.getOperation())
                    .startTimeMs(openSpan.getStartTimeMs())
                    .durationNs(System.nanoTime() - openSpan.getStartClockNs())
                    .build();

            // Notify subscribers iff trace is observable
            if (currentTrace.get().isObservable()) {
                for (SpanObserver observer : observers) {
                    observer.consume(span);
                }
            }

            return Optional.of(span);
        }
    }

    /**
     * Subscribes the given span observer to all "span completed" events. Observers are expected to be "cheap", i.e.,
     * do all non-trivial work (logging, sending network messages, etc) asynchronously.
     */
    public static void subscribe(SpanObserver observer) {
        observers.add(observer);
    }

    /** The inverse of {@link #subscribe}. */
    public static void unsubscribe(SpanObserver observer) {
        observers.remove(observer);
    }

    /** Sets the sampler (for all threads). */
    public static void setSampler(TraceSampler sampler) {
        Tracer.sampler = sampler;
    }

    /** Returns the globally unique identifier for this thread's trace. */
    public static String getTraceId() {
        return currentTrace.get().getTraceId();
    }

    /**
     * True iff the spans of this thread's trace are to be observed by {@link SpanObserver span obververs} upon
     * {@link Tracer#completeSpan span completion}.
     */
    public static boolean isTraceObservable() {
        return currentTrace.get().isObservable();
    }

    private static String validateId(String id, String messageTemplate) {
        // TODO(rfink) Should we check the format?
        Preconditions.checkArgument(id != null && !id.isEmpty(), messageTemplate, id);
        return id;
    }
}
