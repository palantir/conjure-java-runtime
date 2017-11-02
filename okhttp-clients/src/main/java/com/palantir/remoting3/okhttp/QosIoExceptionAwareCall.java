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

import com.palantir.remoting.api.errors.RemoteException;
import java.io.IOException;
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
            return exceptionHandler.handle(this, e);
        } catch (IOException | RemoteException e) {
            throw e;
        } catch (RuntimeException e) {
            throw wrapRuntimeException(e);
        }
    }

    @Override
    public void enqueue(Callback responseCallback) {
        Callback qosAwareCallback = new Callback() {
            @Override
            public void onFailure(Call call, IOException ioException) {
                if (ioException instanceof QosIoException) {
                    handleQosExceptionAsync(QosIoExceptionAwareCall.this, (QosIoException) ioException,
                            responseCallback);
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

    private void handleQosExceptionAsync(QosIoExceptionAwareCall call, QosIoException ioException,
            Callback responseCallback) {
        try {
            exceptionHandler.handleAsync(call, ioException, responseCallback);
        } catch (Throwable t) {
            responseCallback.onFailure(call, wrapRuntimeException(t));
        }
    }

    private IOException wrapRuntimeException(Throwable throwable) {
        return new IOException("Failed to execute request", throwable);
    }

    @Override
    public QosIoExceptionAwareCall doClone() {
        return new QosIoExceptionAwareCall(getDelegate().clone(), exceptionHandler);
    }
}
