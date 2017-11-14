/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.retrofit2;

import java.io.IOException;
import okhttp3.Request;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressWarnings({"checkstyle:noclone", "checkstyle:superclone", "checkstyle:designforextension"})
abstract class ForwardingRetrofitCall<T> implements retrofit2.Call<T> {
    private final retrofit2.Call<T> delegate;

    ForwardingRetrofitCall(retrofit2.Call<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Response<T> execute() throws IOException {
        return delegate.execute();
    }

    @Override
    public void enqueue(Callback<T> userCode) {
        delegate.enqueue(userCode);
    }

    @Override
    public boolean isExecuted() {
        return delegate.isExecuted();
    }

    @Override
    public void cancel() {
        delegate.cancel();
    }

    @Override
    public boolean isCanceled() {
        return delegate.isCanceled();
    }

    @Override
    public retrofit2.Call<T> clone() {
        return doClone();
    }

    @Override
    public Request request() {
        return delegate.request();
    }

    /**
     * Subclasses must provide a clone implementation. It typically returns a new instance of a subclass of {@link
     * ForwardingRetrofitCall}.
     */
    public abstract retrofit2.Call<T> doClone();

    protected retrofit2.Call<T> getDelegate() {
        return delegate;
    }
}
