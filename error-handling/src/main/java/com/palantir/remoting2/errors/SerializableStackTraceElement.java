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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serializable;
import java.util.Optional;
import org.immutables.value.Value;

@JsonDeserialize(as = ImmutableSerializableStackTraceElement.class)
@JsonSerialize(as = ImmutableSerializableStackTraceElement.class)
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class SerializableStackTraceElement implements Serializable {

    public abstract String getClassName();

    public abstract String getMethodName();

    public abstract Optional<String> getFileName();

    public abstract int getLineNumber();

    @Override
    public final String toString() {
        StackTraceElement element = new StackTraceElement(getClassName(),
                getMethodName(),
                getFileName().orElse(null),
                getLineNumber());

        return element.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ImmutableSerializableStackTraceElement.Builder {}
}
