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
import com.google.common.collect.Maps;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The singleton entry point for handling Zipkin-style traces and spans. Provides functionality for starting and
 * completing spans, and for subscribing observers to span completion events.
 * <p>
 * This class is thread-safe.
 */
public final class Tracer {

    private static final Logger log = LoggerFactory.getLogger(Tracer.class);

    private Tracer() {}

    // Thread-safe since thread-local
    private static final ThreadLocal<Trace> currentTrace = new ThreadLocal<Trace>() {
        @Override
        protected Trace initialValue() {
            return new Trace(sampler.sample(), Tracers.randomId());
        }
    };

    // Thread-safe Map implementation
    private static final Map<String, SpanObserver> observers = Maps.newConcurrentMap();

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
     * Opens a new span for this thread's call trace, labeled with the provided operation and parent span. Only allowed
     * when the current trace is empty.
     */
    public static OpenSpan startSpan(String operation, String parentSpanId) {
        Preconditions.checkState(currentTrace.get().isEmpty(),
                "Cannot start a span with explicit parent if the current thread's trace is non-empty");
        validateId(parentSpanId, "parentTraceId must be non-empty: %s");
        OpenSpan span = OpenSpan.builder()
                .spanId(Tracers.randomId())
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
                .spanId(Tracers.randomId());

        Optional<OpenSpan> prevState = currentTrace.get().top();
        if (prevState.isPresent()) {
            spanBuilder.parentSpanId(prevState.get().getSpanId());
        }

        OpenSpan span = spanBuilder.build();
        currentTrace.get().push(span);
        return span;
    }

    /**
     * Adds the given event to this thread's latest open span and returns the augmented span. This method is no-op and
     * returns absent if there is no open span, i.e., if the current trace is empty.
     */
    public static Optional<OpenSpan> addEvent(Event event) {
        Optional<OpenSpan> currentSpan = currentTrace.get().pop();
        if (!currentSpan.isPresent()) {
            return Optional.absent();
        } else {
            OpenSpan newSpan = OpenSpan.builder()
                    .from(currentSpan.get())
                    .addEvents(event)
                    .build();
            currentTrace.get().push(newSpan);
            return Optional.of(newSpan);
        }
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
                    .events(openSpan.events())
                    .parentSpanId(openSpan.getParentSpanId())
                    .operation(openSpan.getOperation())
                    .startTimeMicroSeconds(openSpan.getStartTimeMicroSeconds())
                    .durationNanoSeconds(System.nanoTime() - openSpan.getStartClockNanoSeconds())
                    .build();

            // Notify subscribers iff trace is observable
            if (currentTrace.get().isObservable()) {
                for (SpanObserver observer : observers.values()) {
                    observer.consume(span);
                }
            }

            return Optional.of(span);
        }
    }

    /**
     * Subscribes the given (named) span observer to all "span completed" events. Observers are expected to be "cheap",
     * i.e., do all non-trivial work (logging, sending network messages, etc) asynchronously. If an observer is already
     * registered for the given name, then it gets overwritten by this call. Returns the observer previously associated
     * with the given name, or null if there is no such observer.
     */
    public static SpanObserver subscribe(String name, SpanObserver observer) {
        if (observers.containsKey(name)) {
            log.warn("Overwriting existing SpanObserver with name {} by new observer: {}", name, observer);
        }
        if (observers.size() >= 5) {
            log.warn("Five or more SpanObservers registered: {}", observers.keySet());
        }
        return observers.put(name, observer);
    }

    /**
     * The inverse of {@link #subscribe}: removes the observer registered for the given name. Returns the removed
     * observer if it existed, or null otherwise.
     */
    public static SpanObserver unsubscribe(String name) {
        return observers.remove(name);
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

    /** Returns an independent copy of this thread's {@link Trace}. */
    static Trace copyTrace() {
        return currentTrace.get().deepCopy();
    }

    /**
     * Sets the thread-local trace. Considered an internal API used only for propagating the trace state across threads.
     */
    static void setTrace(Trace trace) {
        currentTrace.set(trace);
    }

    private static String validateId(String id, String messageTemplate) {
        // TODO(rfink) Should we check the format?
        Preconditions.checkArgument(id != null && !id.isEmpty(), messageTemplate, id);
        return id;
    }
}
