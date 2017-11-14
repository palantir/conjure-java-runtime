/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.retrofit2;

import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting3.okhttp.RemoteIoException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import javax.annotation.Nullable;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * This class ensures retrofit2 interfaces that return a plain {@code retrofit2.Call<T>} receive a
 * proper {@link RemoteException} on both their synchronous and async codepaths instead of the
 * {@link RemoteIoException} wrapper class.
 * <p>
 * Similar to the Java8 Competable Future logic in {@link AsyncSerializableErrorCallAdapterFactory}.
 */
public final class UnwrapRemoteIoExceptionCallAdapterFactory extends CallAdapter.Factory {
    public static final CallAdapter.Factory INSTANCE = new UnwrapRemoteIoExceptionCallAdapterFactory();

    @Nullable
    @Override
    public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
        if (getRawType(returnType) != retrofit2.Call.class) {
            return null;
        }
        if (!(returnType instanceof ParameterizedType)) {
            throw new IllegalStateException("Call return type must be parameterized"
                    + " as Call<Foo> or Call<? extends Foo>");
        }
        Type innerType = getParameterUpperBound(0, (ParameterizedType) returnType);

        if (getRawType(innerType) != Response.class) {
            // Generic type is not Response<T>. Use it for body-only adapter.
            return new UnwrapIoExceptionCallAdapter<>(innerType);
        }

        return null;
    }

    private static final class UnwrapIoExceptionCallAdapter<R> implements CallAdapter<R, retrofit2.Call<R>> {
        private final Type responseType;

        UnwrapIoExceptionCallAdapter(Type responseType) {
            this.responseType = responseType;
        }

        @Override
        public Type responseType() {
            return responseType;
        }

        @Override
        public retrofit2.Call<R> adapt(final retrofit2.Call<R> call) {
            return new UnwrapRemoteIoExceptionCall<>(call);
        }
    }

    private static class UnwrapRemoteIoExceptionCall<T> extends ForwardingRetrofitCall<T> {

        UnwrapRemoteIoExceptionCall(retrofit2.Call<T> delegate) {
            super(delegate);
        }

        @Override
        public Response<T> execute() throws IOException {
            try {
                return getDelegate().execute();
            } catch (RemoteIoException e) {
                throw e.getRuntimeExceptionCause();
            }
        }

        @Override
        public void enqueue(Callback<T> userCode) {
            getDelegate().enqueue(new Callback<T>() {
                @Override
                public void onResponse(retrofit2.Call<T> call, Response<T> response) {
                    userCode.onResponse(call, response);
                }

                @Override
                public void onFailure(retrofit2.Call<T> call, Throwable throwable) {
                    if (throwable instanceof RemoteIoException) {
                        RemoteIoException unwrapped = (RemoteIoException) throwable;
                        userCode.onFailure(call, unwrapped.getRuntimeExceptionCause());
                    } else {
                        userCode.onFailure(call, throwable);
                    }
                }
            });
        }

        @Override
        public Call<T> doClone() {
            return new UnwrapRemoteIoExceptionCall<>(getDelegate().clone());
        }
    }
}
