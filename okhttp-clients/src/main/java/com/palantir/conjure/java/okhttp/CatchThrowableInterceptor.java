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

package com.palantir.conjure.java.okhttp;

import com.palantir.logsafe.exceptions.SafeIoException;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * {@link okhttp3.RealCall#execute()} only catches IOExceptions, which means that any non-IOException eventually mean
 * the {@link okhttp3.Dispatcher} runs out of threads and can't make *any* outgoing requests.
 *
 * <p>https://github.com/square/okhttp/issues/5151
 */
enum CatchThrowableInterceptor implements Interceptor {
    INSTANCE;

    @Override
    public Response intercept(Chain chain) throws IOException {
        try {
            return chain.proceed(chain.request());
        } catch (Throwable t) {
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new SafeIoException(
                    "Caught a non-IOException. "
                            + "This is a serious bug and requires investigation. Rethrowing "
                            + "as an IOException in order to avoid blocking a Dispatcher thread",
                    t);
        }
    }
}
