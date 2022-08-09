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

package com.palantir.conjure.java.server.jersey;

import com.palantir.logsafe.Preconditions;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.OptionalInt;
import org.glassfish.jersey.internal.inject.Custom;

/*
 * Adapted from https://github.com/dropwizard/dropwizard/blob/master/dropwizard-jersey/src/main/java/io/dropwizard/jersey/optional/OptionalIntParamConverterProvider.java
 */
// The Custom annotation ensures that our custom param converters are considered first. See ParamConverterFactory.
@Custom
@Provider
public final class Java8OptionalIntParamConverterProvider implements ParamConverterProvider {
    private final OptionalIntParamConverter paramConverter = new OptionalIntParamConverter();

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(
            final Class<T> rawType, final Type _genericType, final Annotation[] _annotations) {
        return OptionalInt.class.equals(rawType) ? (ParamConverter<T>) paramConverter : null;
    }

    public static final class OptionalIntParamConverter implements ParamConverter<OptionalInt> {
        @Override
        public OptionalInt fromString(final String value) {
            if (value == null) {
                return OptionalInt.empty();
            }

            try {
                return OptionalInt.of(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String toString(final OptionalInt value) {
            Preconditions.checkArgument(value != null);
            return value.isPresent() ? Integer.toString(value.getAsInt()) : "";
        }
    }
}
