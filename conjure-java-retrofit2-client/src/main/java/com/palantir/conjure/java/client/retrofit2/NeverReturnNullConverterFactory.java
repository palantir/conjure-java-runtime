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

import com.palantir.logsafe.Preconditions;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Converter.Factory;
import retrofit2.Retrofit;

final class NeverReturnNullConverterFactory extends Factory {

    private final Factory delegate;

    NeverReturnNullConverterFactory(Factory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        Converter<ResponseBody, ?> responseBodyConverter = delegate.responseBodyConverter(type, annotations, retrofit);
        return new Converter<ResponseBody, Object>() {
            @Override
            public Object convert(ResponseBody value) throws IOException {
                Object object = responseBodyConverter.convert(value);
                Preconditions.checkNotNull(object, "Unexpected null body");
                return object;
            }
        };
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter(
            Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
        return delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit);
    }
}
