/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.retrofit2;

import com.palantir.remoting3.okhttp.RemoteIoException;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Retrofit2 equivalent of the jaxrs RemoteIoExceptionClient.
 */
public class UnwrapRemoteIoExceptionCallFactory implements Call.Factory {
    private final Call.Factory delegate;

    public UnwrapRemoteIoExceptionCallFactory(Call.Factory delegate) {
        this.delegate = delegate;
    }

    @Override
    public Call newCall(Request request) {
        return new RemoteIoExceptionCall(delegate.newCall(request));
    }

    private static class RemoteIoExceptionCall implements Call {
        private final Call delegate;

        RemoteIoExceptionCall(Call delegate) {
            this.delegate = delegate;
        }

        @Override
        public Request request() {
            return delegate.request();
        }

        @Override
        public Response execute() throws IOException {
            try {
                return delegate.execute();
            } catch (RemoteIoException e) {
                throw e.getRuntimeExceptionCause();
            }
        }

        @Override
        public void enqueue(Callback responseCallback) {
            // TODO(dfox): tests for this??
            delegate.enqueue(responseCallback);
        }

        @Override
        public void cancel() {
            delegate.cancel();
        }

        @Override
        public boolean isExecuted() {
            return delegate.isExecuted();
        }

        @Override
        public boolean isCanceled() {
            return delegate.isCanceled();
        }

        @Override
        public Call clone() {
            return delegate.clone();
        }
    }
}
