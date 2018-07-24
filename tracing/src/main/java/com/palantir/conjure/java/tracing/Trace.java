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

import static com.google.common.base.Preconditions.checkArgument;

import com.palantir.conjure.java.api.tracing.OpenSpan;
import com.palantir.conjure.java.api.tracing.SpanObserver;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Represents a trace as an ordered list of non-completed spans. Supports adding and removing of spans. This class is
 * not thread-safe and is intended to be used in a thread-local context.
 */
final class Trace {

    private final Deque<OpenSpan> stack;
    private final boolean isObservable;
    private final String traceId;

    private Trace(ArrayDeque<OpenSpan> stack, boolean isObservable, String traceId) {
        checkArgument(!traceId.isEmpty(), "traceId must be non-empty");

        this.stack = stack;
        this.isObservable = isObservable;
        this.traceId = traceId;
    }

    Trace(boolean isObservable, String traceId) {
        this(new ArrayDeque<>(), isObservable, traceId);
    }

    void push(OpenSpan span) {
        stack.push(span);
    }

    Optional<OpenSpan> top() {
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.peekFirst());
    }

    Optional<OpenSpan> pop() {
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.pop());
    }

    boolean isEmpty() {
        return stack.isEmpty();
    }

    /**
     * True iff the spans of this trace are to be observed by {@link SpanObserver span obververs} upon {@link
     * Tracer#completeSpan span completion}.
     */
    boolean isObservable() {
        return isObservable;
    }

    /**
     * The globally unique non-empty identifier for this call trace.
     */
    String getTraceId() {
        return traceId;
    }

    /** Returns a copy of this Trace which can be independently mutated. */
    Trace deepCopy() {
        return new Trace(new ArrayDeque<>(stack), isObservable, traceId);
    }
}
