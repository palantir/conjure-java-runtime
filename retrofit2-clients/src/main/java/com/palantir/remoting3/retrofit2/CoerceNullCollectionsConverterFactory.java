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

package com.palantir.remoting3.retrofit2;

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

/**
 * We want to be lenient and interpret "null" or empty responses (including 204) as the empty value if the expected
 * type is a collection. Jackson can only do this for fields inside an object, but for top-level fields we have to do
 * this manually.
 * <p>
 * {@link Converter.Factory} doesn't support handling the 204 response case, because retrofit will conveniently never
 * call converters for 204/205 responses (see {@link retrofit2.OkHttpCall#parseResponse(Response)}) but instead
 * returns a {code null} body.
 * To handle 204s, we rely on {@link CoerceNullCollectionsCallAdapterFactory}.
 */
// TODO(dsanduleac): link to spec
public final class CoerceNullCollectionsConverterFactory extends Converter.Factory {
    private final Converter.Factory delegate;

    CoerceNullCollectionsConverterFactory(Converter.Factory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        Converter<ResponseBody, ?> responseBodyConverter = delegate.responseBodyConverter(type, annotations, retrofit);
        return new Converter<ResponseBody, Object>() {
            @Override
            public Object convert(ResponseBody value) throws IOException {
                Object object;
                if (value.contentLength() == 0) {
                    object = null;
                } else {
                    object = responseBodyConverter.convert(value);
                }
                if (object == null) {
                    if (!(type instanceof ParameterizedType)) {
                        throw new SafeIllegalStateException("Function must return a ParametrizedType",
                                SafeArg.of("type", type));
                    }
                    Class<?> rawType = getRawType(getParameterUpperBound(0, (ParameterizedType) type));
                    if (List.class.isAssignableFrom(rawType)) {
                        return Collections.emptyList();
                    } else if (Set.class.isAssignableFrom(rawType)) {
                        return Collections.emptySet();
                    } else if (Map.class.isAssignableFrom(rawType)) {
                        return Collections.emptyMap();
                    }
                }
                return object;
            }
        };
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter(Type type, Annotation[] parameterAnnotations,
            Annotation[] methodAnnotations, Retrofit retrofit) {
        return delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit);
    }
}
