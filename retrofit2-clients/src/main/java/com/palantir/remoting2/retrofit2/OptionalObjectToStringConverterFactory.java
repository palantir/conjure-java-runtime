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

package com.palantir.remoting2.retrofit2;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Optional;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * A retrofit2 {@link Converter} that converts {@code Optional<?>} retrofit {@link Path} and {@link Query} parameters
 * into the string representation of the wrapped object, or the empty string if the optional is empty. Handles both
 * {@link java.util.Optional Java8 Optional} and {@link com.google.common.base.Optional Guava Optional}.
 */
public final class OptionalObjectToStringConverterFactory extends Converter.Factory {
    public static final OptionalObjectToStringConverterFactory INSTANCE = new OptionalObjectToStringConverterFactory();

    private OptionalObjectToStringConverterFactory() {}

    @Override
    public Converter<?, String> stringConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        Optional<?> pathQueryAnnotation = ImmutableList.copyOf(annotations).stream()
                .map(Annotation::annotationType)
                .filter(t -> t == Path.class || t == Query.class)
                .findAny();

        if (pathQueryAnnotation.isPresent()) {
            TypeToken typeToken = TypeToken.of(type);
            if (typeToken.getRawType() == java.util.Optional.class) {
                return Java8OptionalStringConverter.INSTANCE;
            } else if (typeToken.getRawType() == com.google.common.base.Optional.class) {
                return GuavaOptionalStringConverter.INSTANCE;
            }
        }

        return null;
    }

    enum Java8OptionalStringConverter implements Converter<java.util.Optional<?>, String> {
        INSTANCE;

        @Override
        public String convert(java.util.Optional<?> value) throws IOException {
            return value.map(Object::toString).orElse("");
        }
    }

    enum GuavaOptionalStringConverter implements Converter<com.google.common.base.Optional<?>, String> {
        INSTANCE;

        @Override
        public String convert(com.google.common.base.Optional<?> value) throws IOException {
            return value.transform(Object::toString).or("");
        }
    }
}
