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

package com.palantir.remoting2.servers.jersey;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.OptionalInt;
import javax.inject.Inject;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

@Provider
public final class Java8OptionalIntParamConverterProvider implements ParamConverterProvider {

    @Inject
    public Java8OptionalIntParamConverterProvider() {}

    @Override
    public <T> ParamConverter<T> getConverter(final Class<T> rawType, final Type genericType,
            final Annotation[] annotations) {
        if (OptionalInt.class.equals(rawType)) {
            return new ParamConverter<T>() {
                @Override
                public T fromString(String value) {
                    if (value == null) {
                        return rawType.cast(OptionalInt.empty());
                    }
                    return rawType.cast(OptionalInt.of(Integer.parseInt(value)));
                }

                @Override
                public String toString(T value) {
                    return value.toString();
                }
            };
        }

        return null;
    }
}
