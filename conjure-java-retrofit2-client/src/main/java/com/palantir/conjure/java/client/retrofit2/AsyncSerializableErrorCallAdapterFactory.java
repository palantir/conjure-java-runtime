/*
 * Copied from Retrofit 2.3.0 and modified; original copyright notice below.
 *
 * See {@link https://github.com/square/retrofit/blob/master/retrofit-adapters/java8/src/main/java/retrofit2/adapter/java8/Java8CallAdapterFactory.java}
 *
 * Copyright (C) 2016 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Changes made:
 * - Made code style comply with Palantir checkstyle.
 * - Removed the ResponseCallAdapter
 * - Made the BodyCallAdapter create RemoteExceptions.
 * - Made the BodyCallAdapter tell the AsyncCallTag that this is an async call.
 * - Added support for Listenable futures in a similar pattern.
 */
package com.palantir.conjure.java.client.retrofit2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.okhttp.IoRemoteException;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

final class AsyncSerializableErrorCallAdapterFactory extends CallAdapter.Factory {
    static final AsyncSerializableErrorCallAdapterFactory INSTANCE = new AsyncSerializableErrorCallAdapterFactory();

    private AsyncSerializableErrorCallAdapterFactory() {}

    @Override
    public CallAdapter<?, ?> get(Type returnType, Annotation[] _annotations, Retrofit _retrofit) {
        Type outerType = getRawType(returnType);
        if (outerType != CompletableFuture.class && outerType != ListenableFuture.class) {
            return null;
        }
        if (!(returnType instanceof ParameterizedType)) {
            throw new SafeIllegalStateException("CompletableFuture/ListenableFuture return type must be parameterized"
                    + " as <Foo> or <? extends Foo>");
        }
        Type innerType = getParameterUpperBound(0, (ParameterizedType) returnType);

        if (getRawType(innerType) != Response.class) {
            // Generic type is not Response<T>. Use it for body-only adapter.
            return getCallAdapter(outerType, innerType);
        }

        return null;
    }

    private static <R> CallAdapter<R, ?> getCallAdapter(Type outerType, Type innerType) {
        if (outerType == ListenableFuture.class) {
            return new ListenableFutureBodyCallAdapter<>(innerType);
        } else if (outerType == CompletableFuture.class) {
            return new CompletableFutureBodyCallAdapter<>(innerType);
        } else {
            return null;
        }
    }

    private static final class ListenableFutureCallback<R> extends AbstractFuture<R> implements Callback<R> {
        private final Call<R> delegate;

        private ListenableFutureCallback(Call<R> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            delegate.cancel();
            return super.cancel(mayInterruptIfRunning);
        }

        @Override
        public void onResponse(Call<R> _call, Response<R> response) {
            boolean futureWasCancelled = !set(response.body());
            if (futureWasCancelled) {
                close(response);
            }
        }

        @Override
        public void onFailure(Call<R> _call, Throwable throwable) {
            // TODO(rfink): Would be good to not leak okhttp internals here
            if (throwable instanceof IoRemoteException) {
                setException(((IoRemoteException) throwable).getWrappedException());
            } else {
                setException(throwable);
            }
        }
    }

    @VisibleForTesting
    static final class ListenableFutureBodyCallAdapter<R> implements CallAdapter<R, ListenableFuture<R>> {
        private final Type responseType;

        ListenableFutureBodyCallAdapter(Type responseType) {
            this.responseType = responseType;
        }

        @Override
        public Type responseType() {
            return responseType;
        }

        @Override
        public ListenableFuture<R> adapt(final Call<R> call) {
            ListenableFutureCallback<R> callback = new ListenableFutureCallback<>(call);
            call.enqueue(callback);
            return callback;
        }
    }

    @VisibleForTesting
    static final class CompletableFutureBodyCallAdapter<R> implements CallAdapter<R, CompletableFuture<R>> {
        private final Type responseType;

        CompletableFutureBodyCallAdapter(Type responseType) {
            this.responseType = responseType;
        }

        @Override
        public Type responseType() {
            return responseType;
        }

        @Override
        public CompletableFuture<R> adapt(final Call<R> call) {
            final CompletableFuture<R> future = new CompletableFuture<R>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    if (mayInterruptIfRunning) {
                        call.cancel();
                    }
                    return super.cancel(mayInterruptIfRunning);
                }
            };

            call.enqueue(new Callback<R>() {
                @Override
                public void onResponse(Call<R> _call, Response<R> response) {
                    boolean futureWasCancelled = !future.complete(response.body());
                    if (futureWasCancelled) {
                        close(response);
                    }
                }

                @Override
                public void onFailure(Call<R> _call, Throwable throwable) {
                    // TODO(rfink): Would be good to not leak okhttp internals here
                    if (throwable instanceof IoRemoteException) {
                        future.completeExceptionally(((IoRemoteException) throwable).getWrappedException());
                    } else {
                        future.completeExceptionally(throwable);
                    }
                }
            });

            return future;
        }
    }

    private static void close(Response<?> response) {
        ResponseBody body = response.raw().body();
        if (body != null) {
            body.close();
        }
    }
}
