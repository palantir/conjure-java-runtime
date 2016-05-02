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

import com.google.common.base.Strings;

public final class Traces {

    public static final String TRACE_HEADER = "trace-id";

    private static final ThreadLocal<TraceState> STATE = new ThreadLocal<TraceState>() {
        @Override
        protected TraceState initialValue() {
            return TraceState.builder().operation("").build();
        }
    };

    public static TraceState getTrace() {
        return STATE.get();
    }

    /**
     * Create a new call trace using the provided operation descriptor and trace identifier.
     * If the provided traceId is null, a random traceId will be generated.
     */
    public static void createTrace(String operation, String traceId) {
        TraceState newState;
        if (!Strings.isNullOrEmpty(traceId)) {
            newState = TraceState.builder().operation(operation).traceId(traceId).build();
        } else {
            newState = TraceState.builder().operation(operation).build();
        }

        STATE.set(newState);
    }

    /**
     * Create a new call trace with the provided operation descriptor and a randomly generated globally unique
     * trace identifier.
     */
    public static void createTrace(String operation) {
        createTrace(operation, null);
    }

    private Traces() {}

}
