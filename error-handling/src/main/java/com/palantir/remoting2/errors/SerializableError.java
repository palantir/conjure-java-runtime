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

package com.palantir.remoting2.errors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * A JSON-serializable representation of a generic Java exception, represented by its exception message, an error name
 * identifying the type of error, and an optional stack trace. Intended to transport exceptions through non-Java
 * channels such as HTTP responses in order to be de-serialized on the receiving end.
 */
@JsonDeserialize(as = ImmutableSerializableError.class)
@JsonSerialize(as = ImmutableSerializableError.class)
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class SerializableError implements Serializable {

    /** A human-readable description of the error. */
    public abstract String getMessage();

    /**
     * A name identifying the type of error; this is typically the fully-qualified name of a server-side exception or
     * some other application-defined string identifying the error. Clients are given access to the server-side error
     * name via {@link RemoteException#getRemoteException} and typically switch&dispatch on the error name.
     * <p>
     * TODO(rfink) This needs to get renamed once a wire-break is acceptable.
     */
    @JsonProperty("exceptionClass")
    public abstract String getErrorName();

    /** An optional representation of the server-side stacktrace of this error. */
    @Nullable
    public abstract List<SerializableStackTraceElement> getStackTrace();

    /** Refer to {@link Throwable#getCause()}. */
    @Nullable
    public abstract SerializableError getCause();

    /** Refer to {@link Throwable#getSuppressed()}. */
    public abstract List<SerializableError> getSuppressed();

    /** Constructs a new error whose error name is the fully-qualified name of the given class. */
    public static SerializableError of(String message, Class<? extends Exception> exceptionClass) {
        return ImmutableSerializableError.builder()
                .message(message)
                .errorName(exceptionClass.getName())
                .build();
    }

    /** Constructs a new error from the given message and name. */
    public static SerializableError of(String message, String errorName) {
        return ImmutableSerializableError.builder()
                .message(message)
                .errorName(errorName)
                .build();
    }

    /** Constructs a new error which built from the Throwable. This includes all causes and suppressed exceptions. */
    public static SerializableError of(Throwable throwable) {
        List<SerializableStackTraceElement> stackTrace = Arrays.stream(throwable.getStackTrace())
                .map(stackTraceElement -> SerializableStackTraceElement.builder()
                        .className(stackTraceElement.getClassName())
                        .methodName(stackTraceElement.getMethodName())
                        .fileName(Optional.ofNullable(stackTraceElement.getFileName()))
                        .lineNumber(stackTraceElement.getLineNumber())
                        .build())
                .collect(Collectors.toList());

        SerializableError cause = Optional.ofNullable(throwable.getCause())
                .map(SerializableError::of)
                .orElse(null);

        List<SerializableError> suppressed = Arrays.stream(throwable.getSuppressed())
                .map(SerializableError::of)
                .collect(Collectors.toList());

        return ImmutableSerializableError.builder()
                .message(Objects.toString(throwable.getMessage()))
                .errorName(throwable.getClass().getName())
                .stackTrace(stackTrace)
                .suppressed(suppressed)
                .cause(cause)
                .build();
    }

    /**
     * Constructs a new error whose error name is the fully-qualified name of the given class, and with the given
     * stack trace.
     *
     * @deprecated Use {@link #of(Throwable)} instead.
     */
    @Deprecated
    public static SerializableError of(
            String message,
            Class<? extends Exception> exceptionClass,
            @Nullable List<StackTraceElement> stackTrace) {
        List<SerializableStackTraceElement> serializableStackTrace = null;
        if (stackTrace != null) {
            // take copy because transformed list doesn't serialize well (Jackson bug?)
            serializableStackTrace = ImmutableList.copyOf(Lists.transform(
                    stackTrace,
                    new Function<StackTraceElement, SerializableStackTraceElement>() {
                        @Nullable
                        @Override
                        public SerializableStackTraceElement apply(@Nullable StackTraceElement input) {
                            return SerializableStackTraceElement.builder()
                                    .className(input.getClassName())
                                    .methodName(input.getMethodName())
                                    .fileName(Optional.ofNullable(input.getFileName()))
                                    .lineNumber(input.getLineNumber())
                                    .build();
                        }
                    }));
        }
        return ImmutableSerializableError.builder()
                .message(message)
                .errorName(exceptionClass.getName())
                .stackTrace(serializableStackTrace)
                .build();
    }

    /**
     * Constructs a new error whose error name is the fully-qualified name of the given class, and with the given
     * stack trace.
     */
    public static SerializableError withCustomStackTrace(
            String message, Class<? extends Exception> exceptionClass, List<SerializableStackTraceElement> stackTrace) {
        return ImmutableSerializableError.builder()
                .message(message)
                .errorName(exceptionClass.getName())
                .stackTrace(stackTrace)
                .build();
    }

    @Override
    public final String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Remote exception of type ");
        builder.append(getErrorName()).append(" thrown: ");
        builder.append(getMessage()).append("\n");
        if (getStackTrace() != null && !getStackTrace().isEmpty()) {
            builder.append("Remote stacktrace:\n");
            for (SerializableStackTraceElement traceElement : getStackTrace()) {
                builder.append("\tat ").append(traceElement).append("\n");
            }
            builder.append("End remote stacktrace");
        } else {
            builder.append("Remote stacktrace not available\n");
        }
        return builder.toString();
    }
}
