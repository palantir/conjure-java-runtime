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

package com.palantir.remoting1.errors;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * A JSON-serializable representation of a generic Java exception, represented by its exception message, exception
 * class, and stacktrace. Intended to transport exceptions through non-Java channels such as HTTP responses in order to
 * be de-serialized and potentially rethrown on the other end.
 */
@JsonDeserialize(as = ImmutableSerializableError.class)
@JsonSerialize(as = ImmutableSerializableError.class)
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class SerializableError {

    public abstract String getMessage();

    public abstract String getExceptionName();

    @Nullable
    public abstract List<StackTraceElement> getStackTrace();

    /** Constructs a new error whose exception name is the fully-qualified name of the given class. */
    public static SerializableError of(String message, Class<? extends Exception> exceptionClass) {
        return ImmutableSerializableError.builder()
                .message(message)
                .exceptionName(exceptionClass.getName())
                .build();
    }

    /**
     * Constructs a new error whose exception name is the fully-qualified name of the given class, and with the given
     * stack trace.
     */
    public static SerializableError of(
            String message, Class<? extends Exception> exceptionClass, List<StackTraceElement> stackTrace) {
        return ImmutableSerializableError.builder()
                .message(message)
                .exceptionName(exceptionClass.getName())
                .stackTrace(stackTrace)
                .build();
    }
}
