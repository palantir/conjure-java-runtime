/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.okhttp;

import java.io.IOException;
import java.util.Optional;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Workaround a bug in okhttp where cancellation in flight results in leaked responses.
 * https://github.com/square/okhttp/blob/d28d2cec21641b61f3d34e05dd52f43a717c2d32/okhttp/src/main/java/okhttp3/RealCall.java#L210-L213
 */
enum ResponseCapturingInterceptor implements Interceptor {
    INSTANCE;

    private static final ThreadLocal<Response> currentResponse = new ThreadLocal<>();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        currentResponse.set(response);
        return response;
    }

    static Optional<Response> getResponse() {
        return Optional.ofNullable(currentResponse.get());
    }

    static void clearThreadState() {
        currentResponse.remove();
    }
}
