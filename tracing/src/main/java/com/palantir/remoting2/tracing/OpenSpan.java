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

package com.palantir.remoting2.tracing;

import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * A value object represented an open (i.e., non-completed) span. Once completed, the span is represented by a {@link
 * Span} object.
 */
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class OpenSpan {

    /**
     * Returns a description of the operation for this event.
     */
    public abstract String getOperation();

    /**
     * Returns the start time in microseconds since epoch start of the span represented by this state.
     * <p>
     * Users of this class should not set this value manually in the builder, it is configured
     * automatically when using the {@link #builder()} static.
     */
    public abstract long getStartTimeMicroSeconds();

    /**
     * Returns the starting clock position in nanoseconds for use in computing span duration.
     * <p>
     * Users of this class should not set this value manually in the builder, it is configured
     * automatically when using the {@link #builder()} static.
     */
    public abstract long getStartClockNanoSeconds();

    /**
     * Returns the identifier of the parent span for the current span, if one exists.
     */
    public abstract Optional<String> getParentSpanId();

    /**
     * Returns a globally unique identifier representing a single span within the call trace.
     */
    public abstract String getSpanId();

    /** Indicates the {@link SpanType} of this span, e.g., a server-side vs. client-side vs local span. */
    public abstract SpanType type();

    /**
     * Returns a map of custom key-value metadata with which spans will be annotated. For example, a "userId" key
     * could be added to associate spans with the requesting user.
     */
    public abstract Map<String, String> getMetadata();

    /**
     * Indicates if this trace state was sampled
     * public abstract boolean isSampled();
     * <p>
     * /**
     * Returns a builder for {@link OpenSpan} pre-initialized to use the current time.
     * <p>
     * Users should not set the {@code startTimeMs} value manually.
     */
    public static Builder builder() {
        return new Builder()
                // TODO(rfink) Use direct access to system microseconds when moving to Java8 / Java9
                .startTimeMicroSeconds(System.currentTimeMillis() * 1000)
                .startClockNanoSeconds(System.nanoTime());
    }

    public static class Builder extends ImmutableOpenSpan.Builder {}
}
