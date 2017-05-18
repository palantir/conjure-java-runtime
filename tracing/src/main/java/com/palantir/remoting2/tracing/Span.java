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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * A value class representing a completed Span, see {@link OpenSpan} for a description of the fields.
 */
@JsonDeserialize(as = ImmutableSpan.class)
@JsonSerialize(as = ImmutableSpan.class)
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class Span {

    public abstract String getTraceId();
    public abstract Optional<String> getParentSpanId();
    public abstract String getSpanId();
    public abstract SpanType type();
    public abstract String getOperation();
    public abstract long getStartTimeMicroSeconds();
    public abstract long getDurationNanoSeconds();
    /**
     * Returns a map of custom key-value metadata with which spans will be annotated. For example, a "userId" key
     * could be added to associate spans with the requesting user.
     */
    public abstract Map<String, String> getMetadata();

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ImmutableSpan.Builder {}

}
