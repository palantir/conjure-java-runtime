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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.remoting.api.errors.QosException;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.Response;

interface QosIoExceptionHandler {
     /**
     * Handles a {@link QosIoException} that was thrown for the given {@link Call} and returns a future for a suitable
     * response. The future can either encapsulate the original exception, or the response to retrying the call at a
     * later time, or the exception thrown by a later retried execution.
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
     *          backed up requests would have the potential to stall all outgoing RPC. The {@link QosIoExceptionHandler}
     *          approach taken here circumnavigates this pitful by re-scheduling call re-execution instead of performing
     *          thread-blocking sleep.
     *      </li>
     * </ul>
     * <p>
     * The end-to-end flow for handling {@link QosException}s in an OkHttp client is as follows:
     * <ul>
     *     <li>
     *         {@link QosIoExceptionInterceptor} detected HTTP status codes pertaining to server-side
     *         {@link QosException}s (e.g., 429 for retry, 503 for unavailable, etc) and throws a corresponding
     *         {@link QosIoException}.
     *     </li>
     *     <li>
     *         A {@link OkHttpClients.QosIoExceptionAwareOkHttpClient} catches all {@link QosIoException}s and passes
     *         on to a configured {@link QosIoExceptionHandler}.
     *     </li>
     *     <li>
     *         The {@link QosIoExceptionHandler} decides to reschedule the request, throw the {@link QosIoException}
     *         to the calling user, redirect the request, etc.
     *     </li>
     * </ul>
     */
    ListenableFuture<Response> handle(QosIoExceptionAwareCall call, QosIoException exception);
}
