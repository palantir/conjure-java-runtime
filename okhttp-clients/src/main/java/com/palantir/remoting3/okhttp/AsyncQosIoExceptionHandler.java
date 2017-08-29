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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import okhttp3.Call;
import okhttp3.Response;

/**
 * Inspects the {@link QosException} thrown by a {@link Call} execution and re-execution of the call on the configured
 * {@link ExecutorService}.
 */
class AsyncQosIoExceptionHandler implements QosIoExceptionHandler {

    private final ExecutorService executorService;
    private final BackoffStrategy backoffStrategy;

    AsyncQosIoExceptionHandler(ExecutorService executorService, BackoffStrategy backoffStrategy) {
        this.executorService = executorService;
        this.backoffStrategy = backoffStrategy;
    }

    @Override
    public CompletableFuture<Response> handle(QosIoExceptionAwareCall call, QosIoException qosIoException) {
        return qosIoException.getQosException().accept(new QosException.Visitor<CompletableFuture<Response>>() {
            @Override
            public CompletableFuture<Response> visit(QosException.Throttle exception) {
                // TODO(rfink): Implement.
                CompletableFuture<Response> response = new CompletableFuture<>();
                response.completeExceptionally(qosIoException);
                return response;
            }

            @Override
            public CompletableFuture<Response> visit(QosException.RetryOther exception) {
                // TODO(rfink): Implement.
                CompletableFuture<Response> response = new CompletableFuture<>();
                response.completeExceptionally(qosIoException);
                return response;
            }

            @Override
            public CompletableFuture<Response> visit(QosException.Unavailable exception) {
                CompletableFuture<Response> response = new CompletableFuture<>();
                if (!backoffStrategy.nextBackoff().isPresent()) {
                    response.completeExceptionally(qosIoException);
                } else {
                    // TODO(rfink): Use duration and schedule retry for later.
                    executorService.execute(createRunnableFor(call, response));
                }
                return response;
            }
        });
    }

    private Runnable createRunnableFor(Call call, CompletableFuture<Response> result) {
        return () -> {
            try {
                result.complete(call.clone().execute());
            } catch (IOException e) {
                result.completeExceptionally(e);
            }
        };
    }
}
