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

package com.palantir.remoting3.servers.jersey;

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

@Provider
public final class ZonedDateTimeParamConverterProvider implements ParamConverterProvider {
    private final ZonedDateTimeParamConverter paramConverter = new ZonedDateTimeParamConverter();

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(final Class<T> rawType, final Type genericType,
                                              final Annotation[] annotations) {
        return ZonedDateTime.class.equals(rawType) ? (ParamConverter<T>) paramConverter : null;
    }

    public static final class ZonedDateTimeParamConverter implements ParamConverter<ZonedDateTime> {
        @Override
        public ZonedDateTime fromString(final String value) {
            return ZonedDateTime.parse(value);
        }

        @Override
        public String toString(final ZonedDateTime value) {
            checkArgument(value != null);
            return value.toOffsetDateTime().toString();
        }
    }
}
