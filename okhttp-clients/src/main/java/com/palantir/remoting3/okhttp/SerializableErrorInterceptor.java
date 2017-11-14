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

package com.palantir.remoting3.okhttp;

import com.palantir.remoting3.errors.SerializableErrorToExceptionConverter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import okhttp3.Interceptor;
import okhttp3.Response;

public enum SerializableErrorInterceptor implements Interceptor {
    INSTANCE;

    @Override
    public Response intercept(Chain chain) throws IOException {
        // Async (Retrofit) calls surface the exception via a handler rather than throwing,
        // see AsyncSerializableErrorCallAdapterFactory
        if (AsyncCallTag.class.isAssignableFrom(chain.request().tag().getClass())) {
            if (((AsyncCallTag) chain.request().tag()).isAsyncCall()) {
                return chain.proceed(chain.request());
            }
        }

        Response response = chain.proceed(chain.request());
        if (!(response.isSuccessful() || response.code() == 101)) {
            Collection<String> contentTypes = response.headers("Content-Type");
            InputStream body = response.body().byteStream();
            RuntimeException runtimeException = SerializableErrorToExceptionConverter.getException(
                    contentTypes, response.code(), body);
            throw new RemoteIoException(runtimeException);
        }

        return response;
    }
}
