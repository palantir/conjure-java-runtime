/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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
