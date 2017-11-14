/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.retrofit2;

import com.palantir.remoting3.okhttp.ForwardingCall;
import com.palantir.remoting3.okhttp.RemoteIoException;
import java.io.IOException;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Ensures synchronous {@link okhttp3.Call} will throw the desired
 * {@link com.palantir.remoting.api.errors.RemoteException} instead of the internal
 * {@link RemoteIoException}.
 *
 * The async codepath is handled by {@link AsyncSerializableErrorCallAdapterFactory}.
 *
 * For the jaxrs version, see RemoteIoExceptionClient.
 */
public final class UnwrapRemoteIoExceptionCallFactory implements okhttp3.Call.Factory {
    private final okhttp3.Call.Factory delegate;

    public UnwrapRemoteIoExceptionCallFactory(okhttp3.Call.Factory delegate) {
        this.delegate = delegate;
    }

    @Override
    public okhttp3.Call newCall(Request request) {
        return new RemoteIoExceptionCall(delegate.newCall(request));
    }

    private static class RemoteIoExceptionCall extends ForwardingCall {
        RemoteIoExceptionCall(okhttp3.Call call) {
            super(call);
        }

        @Override
        public Response execute() throws IOException {
            try {
                return getDelegate().execute();
            } catch (RemoteIoException e) {
                throw e.getRuntimeExceptionCause();
            }
        }

        @Override
        public okhttp3.Call doClone() {
            return new RemoteIoExceptionCall(getDelegate());
        }
    }
}
