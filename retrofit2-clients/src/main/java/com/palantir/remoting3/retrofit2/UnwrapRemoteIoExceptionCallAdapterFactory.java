/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.retrofit2;

import com.palantir.remoting3.okhttp.RemoteIoException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import javax.annotation.Nullable;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class UnwrapRemoteIoExceptionCallAdapterFactory extends CallAdapter.Factory {
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
            System.out.println("HELLO TEAM");
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
            return new DelegatingCall<>(call);
        }
    }

    private static class DelegatingCall<T> implements Call<T> {
        private final Call<T> delegate;

        public DelegatingCall(Call<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Response<T> execute() throws IOException {
            return delegate.execute();
        }

        @Override
        public void enqueue(Callback<T> userCode) {
            delegate.enqueue(new Callback<T>() {
                @Override
                public void onResponse(Call<T> call, Response<T> response) {
//                    if (response.isSuccessful()) {
//                        userCode.onResponse(call, response);
//                        return;
//                    }
//
//                    Collection<String> contentTypes = response.raw().headers("Content-Type");
//                    InputStream body = response.errorBody().byteStream();
//                    RuntimeException exception = SerializableErrorToExceptionConverter.getException(
//                            contentTypes,
//                            response.code(),
//                            body);

                    userCode.onResponse(call, response);
                }

                @Override
                public void onFailure(Call<T> call, Throwable throwable) {
                    if (throwable instanceof RemoteIoException) {
                        userCode.onFailure(call, ((RemoteIoException) throwable).getRuntimeExceptionCause());
                    } else {
                        userCode.onFailure(call, throwable);
                    }
                }
            });
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
        public Call<T> clone() {
            return delegate.clone();
        }

        @Override
        public Request request() {
            return delegate.request();
        }
    }
}
