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
/*
 * Adapted from https://github.com/dropwizard/dropwizard/blob/master/dropwizard-jersey/src/main/java/io/dropwizard/jersey/optional/OptionalLongParamConverterProvider.java
 */

package com.palantir.conjure.java.server.jersey;

import com.palantir.logsafe.Preconditions;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.OptionalLong;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

@Provider
public final class Java8OptionalLongParamConverterProvider implements ParamConverterProvider {
    private final OptionalLongParamConverter paramConverter = new OptionalLongParamConverter();

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(
            final Class<T> rawType, final Type _genericType, final Annotation[] _annotations) {
        return OptionalLong.class.equals(rawType) ? (ParamConverter<T>) paramConverter : null;
    }

    public static final class OptionalLongParamConverter implements ParamConverter<OptionalLong> {
        @Override
        public OptionalLong fromString(final String value) {
            if (value == null) {
                return OptionalLong.empty();
            }

            try {
                return OptionalLong.of(Long.parseLong(value));
            } catch (NumberFormatException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String toString(final OptionalLong value) {
            Preconditions.checkArgument(value != null);
            return value.isPresent() ? Long.toString(value.getAsLong()) : "";
        }
    }
}
