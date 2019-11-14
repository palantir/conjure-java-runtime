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

package com.palantir.conjure.java.client.retrofit2;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import javax.annotation.Nullable;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

public final class OptionalResponseBodyConverterFactory extends Converter.Factory {
    public static final OptionalResponseBodyConverterFactory INSTANCE = new OptionalResponseBodyConverterFactory();

    private OptionalResponseBodyConverterFactory() {}

    @Nullable
    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(
            Type type,
            Annotation[] _annotations,
            Retrofit _retrofit) {
        if (type instanceof ParameterizedType) {
            Type innerType = getParameterUpperBound(0, (ParameterizedType) type);
            Class<?> rwaInnerClass = getRawType(innerType);
            if (ResponseBody.class.isAssignableFrom(rwaInnerClass)) {
                return OptionalResponseBodyConverter.INSTANCE;
            }
        }
        return null;
    }

    private enum OptionalResponseBodyConverter implements Converter<ResponseBody, Optional<ResponseBody>> {
        INSTANCE;

        @Override
        public Optional<ResponseBody> convert(ResponseBody value) {
            return Optional.ofNullable(value);
        }
    }

}
