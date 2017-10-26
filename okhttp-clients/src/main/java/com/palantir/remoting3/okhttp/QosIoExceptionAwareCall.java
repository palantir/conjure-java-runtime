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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.remoting.api.errors.RemoteException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Callback;
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
                } else if (cause instanceof RemoteException) {
                    // TODO(jbaker): don't want to rethrow cause, but need to make changes to remoting-api.
                    throw (RemoteException) cause;
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
    public void enqueue(Callback responseCallback) {
        Callback qosAwareCallback = new Callback() {
            @Override
            public void onFailure(Call call, IOException ioException) {
                if (ioException instanceof QosIoException) {
                    // Let retry handler deal with the call and propagate its result to the responseCallback
                    ListenableFuture<Response> response =
                            exceptionHandler.handle(QosIoExceptionAwareCall.this, (QosIoException) ioException);
                    Futures.addCallback(response, new FutureCallback<Response>() {
                        @Override
                        public void onSuccess(@Nullable Response result) {
                            try {
                                responseCallback.onResponse(call, result);
                            } catch (IOException ioException) {
                                responseCallback.onFailure(call,
                                        new IOException("Unexpected exception when notifying callback", ioException));
                            }
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            if (throwable instanceof QosIoException) {
                                QosIoException qosIoException = (QosIoException) throwable;
                                responseCallback.onFailure(call, new QosIoException(
                                        qosIoException.getQosException(), qosIoException.getResponse()));
                            } else {
                                responseCallback.onFailure(
                                        call, new IOException("Failed to execute request", throwable));
                            }
                        }
                    });
                } else {
                    responseCallback.onFailure(call, ioException);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                responseCallback.onResponse(call, response);
            }
        };
        super.enqueue(qosAwareCallback);
    }

    @Override
    public QosIoExceptionAwareCall doClone() {
        return new QosIoExceptionAwareCall(getDelegate().clone(), exceptionHandler);
    }
}
