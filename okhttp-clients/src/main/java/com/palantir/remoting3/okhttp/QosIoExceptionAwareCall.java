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
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import okhttp3.Call;
import okhttp3.Response;

public final class QosIoExceptionAwareCall extends ForwardingCall {

    private final QosIoExceptionHandler exceptionHandler;

    QosIoExceptionAwareCall(Call delegate, QosIoExceptionHandler exceptionHandler) {
        super(delegate);
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public Response execute() throws IOException {
        try {
            return super.execute();
        } catch (QosIoException e) {
            ListenableFuture<Response> futureResponse = exceptionHandler.handle(this, e);
            try {
                return futureResponse.get();
            } catch (ExecutionException executionException) {
                Throwable cause = executionException.getCause();
                if (cause instanceof QosIoException) {
                    QosIoException qosIoException = (QosIoException) cause;
                    throw new QosIoException(qosIoException.getQosException(), qosIoException.getResponse());
                } else {
                    throw new IOException("Failed to execute request", cause);
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IOException("Failed to execute request (interrupted?)", interruptedException);
            }
        }
    }

    @Override
    public QosIoExceptionAwareCall doClone() {
        return new QosIoExceptionAwareCall(getDelegate().clone(), exceptionHandler);
    }
}
