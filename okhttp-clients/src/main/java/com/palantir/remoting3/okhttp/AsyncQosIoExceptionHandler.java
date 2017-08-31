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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.remoting.api.errors.QosException;
import java.util.concurrent.ExecutorService;
import okhttp3.Call;
import okhttp3.Response;

/**
 * Inspects the {@link QosException} thrown by a {@link Call} execution and re-execution of the call on the configured
 * {@link ExecutorService}.
 */
class AsyncQosIoExceptionHandler implements QosIoExceptionHandler {

    private final ListeningExecutorService executorService;
    private final BackoffStrategy backoffStrategy;

    AsyncQosIoExceptionHandler(ExecutorService executorService, BackoffStrategy backoffStrategy) {
        this.executorService = MoreExecutors.listeningDecorator(executorService);
        this.backoffStrategy = backoffStrategy;
    }

    @Override
    public ListenableFuture<Response> handle(QosIoExceptionAwareCall call, QosIoException qosIoException) {
        return qosIoException.getQosException().accept(new QosException.Visitor<ListenableFuture<Response>>() {
            @Override
            public ListenableFuture<Response> visit(QosException.Throttle exception) {
                // TODO(rfink): Implement.
                return Futures.immediateFailedFuture(qosIoException);
            }

            @Override
            public ListenableFuture<Response> visit(QosException.RetryOther exception) {
                // TODO(rfink): Implement.
                return Futures.immediateFailedFuture(qosIoException);
            }

            @Override
            public ListenableFuture<Response> visit(QosException.Unavailable exception) {
                if (!backoffStrategy.nextBackoff().isPresent()) {
                    return Futures.immediateFailedFuture(qosIoException);
                } else {
                    // TODO(rfink): Use duration and schedule retry for later.
                    return executorService.submit(() -> call.clone().execute());
                }
            }
        });
    }
}
