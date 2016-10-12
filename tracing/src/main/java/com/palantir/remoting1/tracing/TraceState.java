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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.immutables.value.Value;

/**
 * A container object used to hold information about call tracing.
 */
@JsonDeserialize(as = ImmutableTraceState.class)
@JsonSerialize(as = ImmutableTraceState.class)
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class TraceState {

    /**
     * Returns a description of the operation for this event.
     */
    public abstract String getOperation();

    /**
     * Returns the start time in milliseconds of the span represented by this state.
     * <p>
     * Users of this class should not set this value manually in the builder, it is configured
     * automatically when using the {@link #builder()} static.
     */
    public abstract long getStartTimeMs();

    /**
     * Returns the starting clock position in nanoseconds for use in computing span duration.
     * <p>
     * Users of this class should not set this value manually in the builder, it is configured
     * automatically when using the {@link #builder()} static.
     */
    public abstract long getStartClockNs();

    /**
     * Returns a globally unique identifier representing this call trace.
     */
    @SuppressWarnings("checkstyle:designforextension")
    @Value.Default
    public String getTraceId() {
        return randomId();
    }

    /**
     * Returns the identifier of the parent span for the current span, if one exists.
     */
    public abstract Optional<String> getParentSpanId();

    /**
     * Returns a globally unique identifier representing a single span within the call trace.
     */
    @SuppressWarnings("checkstyle:designforextension")
    @Value.Default
    public String getSpanId() {
        return randomId();
    }

    /**
     * Returns a builder for {@link TraceState} pre-initialized to use the current time.
     * <p>
     * Users should not set the {@code startTimeMs} value manually.
     */
    public static Builder builder() {
        return ImmutableTraceState.builder()
                .startTimeMs(System.currentTimeMillis())
                .startClockNs(System.nanoTime());
    }

    public static String randomId() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong());
    }

    /**
     * A safe builder interface that does not expose setting generated values.
     */
    public interface Builder {
        Builder operation(String operation);

        Builder traceId(String traceId);

        Builder parentSpanId(Optional<String> parentSpanId);

        Builder parentSpanId(String parentSpanId);

        Builder spanId(String spanId);

        TraceState build();
    }

}
