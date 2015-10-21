/*
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
