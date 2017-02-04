/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.jaxrs.feignimpl;

import com.google.common.collect.ImmutableList;
import feign.Param.Expander;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * Expands Optional by using null for {@link Optional#empty()} and the {@link Object#toString()} of the
 * value otherwise.
 */
public final class Java8NullOptionalExpander implements Expander {
    private static final List<OptionalType> OPTIONAL_TYPES = ImmutableList.of(
            new NormalOptionalType(),
            new OptionalIntType()
    );

    @Override
    public String expand(Object value) {
        OptionalType selectedOptionalType = OPTIONAL_TYPES.stream()
                .filter(optionalType -> optionalType.isAssignableFrom(value))
                .findFirst()
                .orElseThrow(() -> {
                    List<Class> clazzes = OPTIONAL_TYPES.stream()
                            .map(OptionalType::clazz)
                            .collect(Collectors.toList());

                    return new RuntimeException(String.format("Value must be one of type %s. Was: %s", clazzes, value.getClass()));
                });

        return selectedOptionalType.asNullableString(value);
    }

    interface OptionalType<T> {
        Class<?> clazz();
        boolean isPresent(T value);
        String asString(T value);

        default boolean isAssignableFrom(Object obj) {
            return clazz().isAssignableFrom(obj.getClass());
        }

        default String asNullableString(T value) {
            return isPresent(value) ? asString(value) : null;
        }
    }

    private static class NormalOptionalType implements OptionalType<Optional> {
        @Override
        public Class<?> clazz() {
            return Optional.class;
        }

        @Override
        public boolean isPresent(Optional value) {
            return value.isPresent();
        }

        @Override
        public String asString(Optional value) {
            return Objects.toString(value.get());
        }
    }

    private static class OptionalIntType implements OptionalType<OptionalInt> {
        @Override
        public Class<?> clazz() {
            return OptionalInt.class;
        }

        @Override
        public boolean isPresent(OptionalInt value) {
            return value.isPresent();
        }

        @Override
        public String asString(OptionalInt value) {
            return Integer.toString(value.getAsInt());
        }
    }
}
