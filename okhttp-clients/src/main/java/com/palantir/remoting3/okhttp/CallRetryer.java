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

package com.palantir.remoting3.okhttp;

import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

interface CallRetryer {
    /**
     * Attempts to execute the given {@link Call} and, if a {@link QosIoException} thrown, potentially
     * retries it at a later time. If the maximum allowed retries are exhausted, an exception will be thrown. This
     * exception will either be the original exception, or an exception thrown by a later retry.
     *
     * <p>
     * Note that vanilla OkHttp functionality does not cover what's needed here, for example:
     * <ul>
     *     <li>
     *         OkHttp doesn't have out-of-the-box support for re-locating complex requests,
     *         cf. https://github.com/square/okhttp/issues/3111 .
     *      </li>
     *      <li>
     *          OkHttp {@link Interceptor}s could wait-and-retry upon observing 429 or 503, but this would happen on the
     *          same thread. Since all http-remoting clients (in a JVM) share the same OkHttp thread pool, only a few
     *          backed up requests would have the potential to stall all outgoing RPC. The {@link CallRetryer}
     *          approach taken here circumnavigates this pitfall by re-scheduling call re-execution instead of
     *          performing thread-blocking sleep for async calls.
     *      </li>
     * </ul>
     * <p>
     * The end-to-end flow for handling {@link QosException}s in an OkHttp client is as follows:
     * <ul>
     *     <li>
     *         {@link QosRetryLaterInterceptor} detected HTTP status codes pertaining to server-side
     *         {@link QosException}s (e.g., 429 for retry, 503 for unavailable, etc) and throws a corresponding
     *         {@link QosIoException}.
     *     </li>
     *     <li>
     *         A {@link OkHttpClients.QosIoExceptionAwareOkHttpClient} catches all {@link QosIoException}s and passes
     *         on to a configured {@link CallRetryer}.
     *     </li>
     *     <li>
     *         The {@link CallRetryer} decides to reschedule the request, throw the {@link QosIoException}
     *         to the calling user, redirect the request, etc.
     *     </li>
     * </ul>
     */
    Response executeWithRetry(Call call) throws IOException;

    /**
     * Performs the same logic as {@link #executeWithRetry}, except that the request will be retried asynchronously
     * via {@link Call#enqueue}, using the provided callback. This method does not block the calling thread.
     */
    void enqueueWithRetry(Call call, Callback callback);

}
