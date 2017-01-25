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

package com.palantir.remoting1.retrofit2;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.lang.annotation.Annotation;
import javax.annotation.Nonnull;
import org.junit.Test;
import retrofit2.http.Path;
import retrofit2.http.Query;

public final class OptionalObjectToStringConverterFactoryTest {

    private OptionalObjectToStringConverterFactory factory = OptionalObjectToStringConverterFactory.INSTANCE;
    private OptionalObjectToStringConverterFactory.GuavaOptionalStringConverter guavaConverter =
            OptionalObjectToStringConverterFactory.GuavaOptionalStringConverter.INSTANCE;
    private OptionalObjectToStringConverterFactory.Java8OptionalStringConverter java8Converter =
            OptionalObjectToStringConverterFactory.Java8OptionalStringConverter.INSTANCE;

    @Test
    public void testRequiresPathOrQueryAnnotation() throws Exception {
        // Java8
        assertThat(factory.stringConverter(java.util.Optional.class, createAnnotations(Path.class), null)).isNotNull();
        assertThat(factory.stringConverter(java.util.Optional.class, createAnnotations(Query.class), null)).isNotNull();
        assertThat(factory.stringConverter(java.util.Optional.class, createAnnotations(Nonnull.class), null)).isNull();

        // Guava
        assertThat(factory.stringConverter(com.google.common.base.Optional.class, createAnnotations(Path.class), null))
                .isNotNull();
        assertThat(factory.stringConverter(com.google.common.base.Optional.class, createAnnotations(Query.class), null))
                .isNotNull();
        assertThat(
                factory.stringConverter(com.google.common.base.Optional.class, createAnnotations(Nonnull.class), null))
                .isNull();
    }

    @Test
    public void testUnwrapsJava8Optional() throws Exception {
        assertThat(guavaConverter.convert(guavaOptional("foo"))).isEqualTo("foo");
        assertThat(guavaConverter.convert(guavaOptional(null))).isEqualTo("");
        assertThat(java8Converter.convert(java8Optional("foo"))).isEqualTo("foo");
        assertThat(java8Converter.convert(java8Optional(null))).isEqualTo("");
    }

    @Test
    public void testSerializesWrappedObjectToString() throws Exception {
        assertThat(guavaConverter.convert(guavaOptional(ImmutableList.of(1, 2)))).isEqualTo("[1, 2]");
        assertThat(guavaConverter.convert(guavaOptional(new Object()))).startsWith("java.lang.Object@");
        assertThat(java8Converter.convert(java8Optional(ImmutableList.of(1, 2)))).isEqualTo("[1, 2]");
        assertThat(java8Converter.convert(java8Optional(new Object()))).startsWith("java.lang.Object@");
    }

    private static <T> com.google.common.base.Optional<T> guavaOptional(T value) {
        return com.google.common.base.Optional.fromNullable(value);
    }

    private static <T> java.util.Optional<T> java8Optional(T value) {

        return java.util.Optional.ofNullable(value);
    }

    private Annotation[] createAnnotations(Class... clazz) {
        Annotation[] annotations = new Annotation[clazz.length];
        for (int i = 0; i < clazz.length; ++i) {
            annotations[i] = sun.reflect.annotation.AnnotationParser.annotationForMap(clazz[i], ImmutableMap.of());
        }
        return annotations;
    }
}
