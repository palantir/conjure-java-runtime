/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http.errors;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@JsonDeserialize(as = ImmutableSerializableError.class)
@JsonSerialize(as = ImmutableSerializableError.class)
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class SerializableError {

    public abstract String getMessage();

    public abstract Class<? extends Exception> getExceptionClass();

    @Nullable
    public abstract List<StackTraceElement> getStackTrace();

    public static SerializableError of(
            String message, Class<? extends Exception> exceptionClass, List<StackTraceElement> stackTrace) {
        return ImmutableSerializableError.builder()
                .message(message)
                .exceptionClass(exceptionClass)
                .stackTrace(stackTrace)
                .build();
    }
}
