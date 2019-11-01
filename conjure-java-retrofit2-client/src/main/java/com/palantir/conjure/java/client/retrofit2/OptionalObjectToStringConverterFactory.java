/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.retrofit2;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Optional;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * A retrofit2 {@link Converter} that converts {@code Optional<?>} retrofit {@link Path}, {@link Query} and {@link
 * Header} parameters into the string representation of the wrapped object, or null if the optional is empty. Handles
 * both {@link java.util.Optional Java8 Optional} and {@link com.google.common.base.Optional Guava Optional}.
 */
public final class OptionalObjectToStringConverterFactory extends Converter.Factory {
    public static final OptionalObjectToStringConverterFactory INSTANCE = new OptionalObjectToStringConverterFactory();

    private OptionalObjectToStringConverterFactory() {}

    @Override
    public Converter<?, String> stringConverter(Type type, Annotation[] annotations, Retrofit _retrofit) {
        Optional<?> pathQueryAnnotation = ImmutableList.copyOf(annotations).stream()
                .map(Annotation::annotationType)
                .filter(t -> t == Path.class || t == Query.class || t == Header.class)
                .findAny();

        if (pathQueryAnnotation.isPresent()) {
            TypeToken<?> typeToken = TypeToken.of(type);

            Optional<Converter<?, String>> converter = getNullableConverterForRawType(typeToken.getRawType())
                    .map(conv -> {
                        if (pathQueryAnnotation.get() == Path.class) {
                            // for paths, we want to turn null -> empty string
                            @SuppressWarnings("unchecked")
                            Converter<Object, String> castConverter = (Converter<Object, String>) conv;
                            return val -> Optional.ofNullable(castConverter.convert(val)).orElse("");
                        } else {
                            return conv;
                        }
                    });

            return converter.orElse(null);
        }

        return null;
    }

    /** Optionally returns a converter which returns null when the value is not present. */
    private static Optional<Converter<?, String>> getNullableConverterForRawType(Class<?> rawType) {
        if (rawType == java.util.Optional.class) {
            return Optional.of(Java8OptionalStringConverter.INSTANCE);
        } else if (rawType == java.util.OptionalInt.class) {
            return Optional.of(Java8OptionalIntStringConverter.INSTANCE);
        } else if (rawType == java.util.OptionalDouble.class) {
            return Optional.of(Java8OptionalDoubleStringConverter.INSTANCE);
        } else if (rawType == java.util.OptionalLong.class) {
            return Optional.of(Java8OptionalLongStringConverter.INSTANCE);
        } else if (rawType == com.google.common.base.Optional.class) {
            return Optional.of(GuavaOptionalStringConverter.INSTANCE);
        } else {
            return Optional.empty();
        }
    }

    enum Java8OptionalStringConverter implements Converter<java.util.Optional<?>, String> {
        INSTANCE;

        @Override
        public String convert(java.util.Optional<?> value) throws IOException {
            return value.map(Object::toString).orElse(null);
        }
    }

    enum Java8OptionalIntStringConverter implements Converter<java.util.OptionalInt, String> {
        INSTANCE;

        @Override
        public String convert(java.util.OptionalInt value) throws IOException {
            return value.isPresent() ? Integer.toString(value.getAsInt()) : null;
        }
    }

    enum Java8OptionalDoubleStringConverter implements Converter<java.util.OptionalDouble, String> {
        INSTANCE;

        @Override
        public String convert(java.util.OptionalDouble value) throws IOException {
            return value.isPresent() ? Double.toString(value.getAsDouble()) : null;
        }
    }

    enum Java8OptionalLongStringConverter implements Converter<java.util.OptionalLong, String> {
        INSTANCE;

        @Override
        public String convert(java.util.OptionalLong value) throws IOException {
            return value.isPresent() ? Long.toString(value.getAsLong()) : null;
        }
    }

    enum GuavaOptionalStringConverter implements Converter<com.google.common.base.Optional<?>, String> {
        INSTANCE;

        @Override
        public String convert(com.google.common.base.Optional<?> value) throws IOException {
            return value.transform(Object::toString).orNull();
        }
    }
}
