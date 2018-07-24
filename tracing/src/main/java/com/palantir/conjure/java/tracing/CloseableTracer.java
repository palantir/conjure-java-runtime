/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.conjure.java.api.tracing.SpanType;

/**
 * Wraps the {@link Tracer} methods in a closeable resource to enable the usage of the try-with-resources pattern.
 *
 * Usage:
 *   try (CloseableTracer trace = CloseableTracer.start("traceName")) {
 *       [...]
 *   }
 *
 * Note that network calls using remoting will be automatically wrapped with appropriate traces. Only use this
 * to add traces to internal code.
 */
public final class CloseableTracer implements AutoCloseable {
    private static final CloseableTracer INSTANCE = new CloseableTracer();

    private CloseableTracer() { }

    /**
     * Opens a new {@link SpanType#LOCAL LOCAL} span for this thread's call trace, labeled with the provided operation.
     */
    public static CloseableTracer startSpan(String operation) {
        Tracer.startSpan(operation);
        return INSTANCE;
    }

    @Override
    public void close() {
        Tracer.fastCompleteSpan();
    }
}
