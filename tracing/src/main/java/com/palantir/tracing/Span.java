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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Optional;
import org.immutables.value.Value;

/**
 * A value class representing a completed Span.
 */
@JsonDeserialize(as = ImmutableSpan.class)
@JsonSerialize(as = ImmutableSpan.class)
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class Span {

    public abstract String getTraceId();

    public abstract Optional<String> getParentSpanId();

    public abstract String getSpanId();

    public abstract String getOperation();

    public abstract long getStartTimeMs();

    public abstract long getDurationNs();

    public static final Builder builder() {
        return new Builder();
    }

    public static class Builder extends ImmutableSpan.Builder {}

}
