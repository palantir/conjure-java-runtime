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

package com.palantir.tracing;

import com.google.common.base.Optional;
import java.util.ArrayDeque;
import java.util.Deque;

public final class Traces {

    public interface Headers {
        String TRACE_ID = "X-B3-TraceId";
        String PARENT_SPAN_ID = "X-B3-ParentSpanId";
        String SPAN_ID = "X-B3-SpanId";
        String SPAN_NAME = "X-Span-Name";
    }

    // stack of trace states
    private static final ThreadLocal<Deque<TraceState>> STATE = new ThreadLocal<Deque<TraceState>>() {
        @Override
        protected Deque<TraceState> initialValue() {
            return new ArrayDeque<>();
        }
    };

    public static Optional<TraceState> getTrace() {
        Deque<TraceState> stack = STATE.get();
        return (!stack.isEmpty()) ? Optional.of(stack.peek()) : Optional.<TraceState>absent();
    }

    public static void setTrace(TraceState state) {
        STATE.get().push(state);
    }

    /**
     * Derives a new call trace from the currently known call trace labeled with the
     * provided operation.
     */
    public static TraceState deriveTrace(String operation) {
        Optional<TraceState> prevState = getTrace();

        TraceState.Builder newStateBuilder = TraceState.builder()
                .operation(operation);

        if (prevState.isPresent()) {
            newStateBuilder.traceId(prevState.get().getTraceId())
                .parentSpanId(prevState.get().getSpanId()); // span -> parent
        }

        TraceState newState = newStateBuilder.build();
        STATE.get().push(newState);
        return newState;
    }

    public static Optional<TraceState> complete() {
        Deque<TraceState> stack = STATE.get();
        return (!stack.isEmpty()) ? Optional.of(stack.pop()) : Optional.<TraceState>absent();
    }

    private Traces() {}

}
