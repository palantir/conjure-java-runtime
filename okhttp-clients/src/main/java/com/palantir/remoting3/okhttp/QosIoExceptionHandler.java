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

import java.util.concurrent.CompletableFuture;
import okhttp3.Call;
import okhttp3.Response;

interface QosIoExceptionHandler {
    /**
     * Handles a {@link QosIoException} that was thrown for the given {@link Call} and returns a future for a suitable
     * response. The future can either encapsulate the original exception, or the response to retrying the call at a
     * later time, or the exception thrown by a later retried execution.
     */
    CompletableFuture<Response> handle(Call call, QosIoException exception);
}
