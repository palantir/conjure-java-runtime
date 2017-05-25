/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.retrofit2;

import com.palantir.remoting2.errors.SerializableErrorToExceptionConverter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import okhttp3.Interceptor;
import okhttp3.Response;

public enum SerializableErrorInterceptor implements Interceptor {
    INSTANCE;

    @Override
    public Response intercept(Chain chain) throws IOException {
        if (((AsyncCallTag) chain.request().tag()).isAsyncCall()) {
            return chain.proceed(chain.request());
        }

        Response response = chain.proceed(chain.request());
        if (!response.isSuccessful()) {
            Collection<String> contentTypes = response.headers("Content-Type");
            InputStream body = response.body().byteStream();
            throw SerializableErrorToExceptionConverter.getException(
                    contentTypes, response.code(), response.message(), body);
        }

        return response;
    }
}
