/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.serialization.types;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Generated;

// copied from the verification subproject to avoid a circular dependency
@JsonDeserialize(builder = OptionalExample.Builder.class)
@Generated("com.palantir.conjure.java.types.BeanGenerator")
public final class OptionalExample {
    private final Optional<String> value;

    private volatile int memoizedHashCode;

    private OptionalExample(Optional<String> value) {
        validateFields(value);
        this.value = value;
    }

    @JsonProperty("value")
    public Optional<String> getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof OptionalExample && equalTo((OptionalExample) other));
    }

    private boolean equalTo(OptionalExample other) {
        return this.value.equals(other.value);
    }

    @Override
    public int hashCode() {
        if (memoizedHashCode == 0) {
            memoizedHashCode = Objects.hash(value);
        }
        return memoizedHashCode;
    }

    @Override
    public String toString() {
        return new StringBuilder("OptionalExample")
                .append('{')
                .append("value")
                .append(": ")
                .append(value)
                .append('}')
                .toString();
    }

    public static OptionalExample of(String value) {
        return builder().value(Optional.of(value)).build();
    }

    private static void validateFields(Optional<String> value) {
        List<String> missingFields = null;
        missingFields = addFieldIfMissing(missingFields, value, "value");
        if (missingFields != null) {
            throw new SafeIllegalArgumentException(
                    "Some required fields have not been set",
                    SafeArg.of("missingFields", missingFields));
        }
    }

    private static List<String> addFieldIfMissing(
            List<String> prev, Object fieldValue, String fieldName) {
        List<String> missingFields = prev;
        if (fieldValue == null) {
            if (missingFields == null) {
                missingFields = new ArrayList<>(1);
            }
            missingFields.add(fieldName);
        }
        return missingFields;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Generated("com.palantir.conjure.java.types.BeanBuilderGenerator")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Builder {
        private Optional<String> value = Optional.empty();

        private Builder() {}

        public Builder from(OptionalExample other) {
            value(other.getValue());
            return this;
        }

        @JsonSetter("value")
        public Builder value(Optional<String> value) {
            this.value = Preconditions.checkNotNull(value, "value cannot be null");
            return this;
        }

        public Builder value(String value) {
            this.value = Optional.of(Preconditions.checkNotNull(value, "value cannot be null"));
            return this;
        }

        public OptionalExample build() {
            return new OptionalExample(value);
        }
    }
}
