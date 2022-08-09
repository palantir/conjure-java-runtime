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
import java.time.Instant;
import org.glassfish.jersey.internal.inject.Custom;

// The Custom annotation ensures that our custom param converters are considered first. See ParamConverterFactory.
@Custom
@Provider
public final class InstantParamConverterProvider implements ParamConverterProvider {
    private final InstantParamConverter paramConverter = new InstantParamConverter();

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(
            final Class<T> rawType, final Type _genericType, final Annotation[] _annotations) {
        return Instant.class.equals(rawType) ? (ParamConverter<T>) paramConverter : null;
    }

    public static final class InstantParamConverter implements ParamConverter<Instant> {
        @Override
        public Instant fromString(final String value) {
            return Instant.parse(value);
        }

        @Override
        public String toString(final Instant value) {
            Preconditions.checkArgument(value != null);
            return value.toString();
        }
    }
}
