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

import com.google.common.collect.ImmutableList;
import com.palantir.remoting.api.tracing.OpenSpan;
import com.palantir.remoting.api.tracing.Span;
import com.palantir.remoting.api.tracing.SpanObserver;
import com.palantir.remoting.api.tracing.SpanType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    // Only access in a class-synchronized fashion
    private static final Map<String, SpanObserver> observers = new HashMap<>();
    // we want iterating through tracers to be very fast, and it's faster to iterate through a list than a Map.values()
    private static volatile List<SpanObserver> observersList = ImmutableList.of();

    /**
     * Initializes the current thread's trace, erasing any previously accrued open spans. The new trace is {@link
     * Trace#isObservable observable} iff the given flag is true, or, iff {@code isObservable} is absent, if the {@link
     * #setSampler configured sampler} returns true.
     */
    public static void initTrace(Optional<Boolean> isObservable, String traceId) {
        com.palantir.tracing.Tracer.initTrace(isObservable, traceId);
    }

    /**
     * Opens a new span for this thread's call trace, labeled with the provided operation and parent span. Only allowed
     * when the current trace is empty.
     */
    public static OpenSpan startSpan(String operation, String parentSpanId, SpanType type) {
        return toRemotingOpenSpan(com.palantir.tracing.Tracer.startSpan(
                operation, parentSpanId, type.asConjure()));
    }

    /**
     * Like {@link #startSpan(String)}, but opens a span of the explicitly given {@link SpanType span type}.
     */
    public static OpenSpan startSpan(String operation, SpanType type) {
        return toRemotingOpenSpan(com.palantir.tracing.Tracer.startSpan(operation, type.asConjure()));
    }

    /**
     * Opens a new {@link SpanType#LOCAL LOCAL} span for this thread's call trace, labeled with the provided operation.
     */
    public static OpenSpan startSpan(String operation) {
        return toRemotingOpenSpan(com.palantir.tracing.Tracer.startSpan(operation));
    }


    /**
     * Completes the current span (if it exists) and notifies all {@link #observers subscribers} about the completed
     * span.
     * <p>
     * Does not construct the Span object if no subscriber will see it.
     */
    public static void fastCompleteSpan() {
        com.palantir.tracing.Tracer.fastCompleteSpan();
    }

    /**
     * Like {@link #fastCompleteSpan()}, but adds {@code metadata} to the current span being completed.
     */
    public static void fastCompleteSpan(Map<String, String> metadata) {
        com.palantir.tracing.Tracer.fastCompleteSpan(metadata);
    }

    /**
     * Completes and returns the current span (if it exists) and notifies all {@link #observers subscribers} about the
     * completed span.
     */
    public static Optional<Span> completeSpan() {
        return com.palantir.tracing.Tracer.completeSpan().map(Tracer::toRemotingSpan);
    }

    /**
     * Like {@link #completeSpan()}, but adds {@code metadata} to the current span being completed.
     */
    public static Optional<Span> completeSpan(Map<String, String> metadata) {
        return com.palantir.tracing.Tracer.completeSpan(metadata).map(Tracer::toRemotingSpan);
    }

    /**
     * Subscribes the given (named) span observer to all "span completed" events. Observers are expected to be "cheap",
     * i.e., do all non-trivial work (logging, sending network messages, etc) asynchronously. If an observer is already
     * registered for the given name, then it gets overwritten by this call. Returns the observer previously associated
     * with the given name, or null if there is no such observer.
     */
    public static synchronized SpanObserver subscribe(String name, SpanObserver observer) {
        return toRemotingSpanObserver(com.palantir.tracing.Tracer.subscribe(name, observer.asConjure()));
    }

    /**
     * The inverse of {@link #subscribe}: removes the observer registered for the given name. Returns the removed
     * observer if it existed, or null otherwise.
     */
    public static synchronized SpanObserver unsubscribe(String name) {
        return toRemotingSpanObserver(com.palantir.tracing.Tracer.unsubscribe(name));
    }

    /** Sets the sampler (for all threads). */
    public static void setSampler(TraceSampler sampler) {
        com.palantir.tracing.Tracer.setSampler(sampler.asConjure());
    }

    /** Returns the globally unique identifier for this thread's trace. */
    public static String getTraceId() {
        return com.palantir.tracing.Tracer.getTraceId();
    }

    /** Clears the current trace id and returns (a copy of) it. */
    public static Trace getAndClearTrace() {
        return com.palantir.tracing.Tracer.getAndClearTrace();
    }

    /**
     * True iff the spans of this thread's trace are to be observed by {@link SpanObserver span obververs} upon {@link
     * Tracer#completeSpan span completion}.
     */
    public static boolean isTraceObservable() {
        return com.palantir.tracing.Tracer.isTraceObservable();
    }

    private static Span toRemotingSpan(com.palantir.tracing.api.Span span) {
        return Span.builder()
                .traceId(span.getTraceId())
                .parentSpanId(span.getParentSpanId())
                .spanId(span.getSpanId())
                .type(toRemotingSpanType(span.type()))
                .operation(span.getOperation())
                .startTimeMicroSeconds(span.getStartTimeMicroSeconds())
                .durationNanoSeconds(span.getDurationNanoSeconds())
                .build();
    }

    private static OpenSpan toRemotingOpenSpan(com.palantir.tracing.api.OpenSpan openSpan) {
        return OpenSpan.builder()
                .parentSpanId(openSpan.getParentSpanId())
                .spanId(openSpan.getSpanId())
                .type(toRemotingSpanType(openSpan.type()))
                .operation(openSpan.getOperation())
                .startTimeMicroSeconds(openSpan.getStartTimeMicroSeconds())
                .startClockNanoSeconds(openSpan.getStartClockNanoSeconds())
                .build();
    }

    private static SpanType toRemotingSpanType(com.palantir.tracing.api.SpanType type) {
        switch (type) {
            case CLIENT_OUTGOING:
                return SpanType.CLIENT_OUTGOING;
            case SERVER_INCOMING:
                return SpanType.SERVER_INCOMING;
            case LOCAL:
                return SpanType.LOCAL;
        }

        throw new UnsupportedOperationException("Unable to convert to Remoting SpanType");
    }

    private static SpanObserver toRemotingSpanObserver(com.palantir.tracing.api.SpanObserver spanObserver) {
        return new SpanObserver() {
            @Override
            public void consume(Span span) {
                spanObserver.consume(span.asConjure());
            }

            @Override
            public com.palantir.tracing.api.SpanObserver asConjure() {
                return spanObserver;
            }
        };
    }
}
