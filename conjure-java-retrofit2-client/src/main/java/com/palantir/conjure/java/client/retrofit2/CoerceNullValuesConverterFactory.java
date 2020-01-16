/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

/**
 * Handles 2xx HTTP responses and interprets "null" or empty responses as the empty value if the expected type is a
 * collection.
 *
 * <p>Note that {@link retrofit2.OkHttpCall#parseResponse(Response)} does not call this ConverterFactory if the response
 * is 204 or 205 - those cases are handled by {@link CoerceNullCollectionsCallAdapterFactory}.
 *
 * <p>(Jackson can only do this coercion for fields inside an object, but for top-level fields we have to do this
 * manually.)
 */
// TODO(dsanduleac): link to spec
final class CoerceNullValuesConverterFactory extends Converter.Factory {
    private final Converter.Factory delegate;

    CoerceNullValuesConverterFactory(Converter.Factory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        Converter<ResponseBody, ?> responseBodyConverter = delegate.responseBodyConverter(type, annotations, retrofit);
        return new CoerceNullCollectionsConverter(responseBodyConverter, type);
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter(
            Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
        return delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit);
    }

    private static class CoerceNullCollectionsConverter implements Converter<ResponseBody, Object> {
        private final Converter<ResponseBody, ?> responseBodyConverter;
        private final Type type;

        CoerceNullCollectionsConverter(Converter<ResponseBody, ?> responseBodyConverter, Type type) {
            this.responseBodyConverter = responseBodyConverter;
            this.type = type;
        }

        @SuppressWarnings("CyclomaticComplexity")
        @Override
        public Object convert(ResponseBody value) throws IOException {
            Object object = (value.contentLength() == 0) ? null : responseBodyConverter.convert(value);
            if (object != null) {
                return object;
            }

            // responseBodyConverter returns null if the value was the literal 'null', so we can do our coercion
            Class<?> rawType = getRawType(type);
            if (List.class.isAssignableFrom(rawType)) {
                return Collections.emptyList();
            } else if (Set.class.isAssignableFrom(rawType)) {
                return Collections.emptySet();
            } else if (Map.class.isAssignableFrom(rawType)) {
                return Collections.emptyMap();
            } else if (rawType == java.util.Optional.class) {
                return Optional.empty();
            } else if (rawType == java.util.OptionalInt.class) {
                return OptionalInt.empty();
            } else if (rawType == java.util.OptionalLong.class) {
                return OptionalLong.empty();
            } else if (rawType == java.util.OptionalDouble.class) {
                return OptionalDouble.empty();
            } else if (rawType == com.google.common.base.Optional.class) {
                return com.google.common.base.Optional.absent();
            }

            return null;
        }
    }
}
