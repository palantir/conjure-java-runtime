/*
 * Copied from Retrofit 2.3.0 and modified; original copyright notice below.
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
 */
package com.palantir.remoting3.retrofit2;

import com.palantir.remoting3.okhttp.RemoteIoException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

/*
 * Changes from {@link https://github.com/square/retrofit/blob/parent-2.3.0/retrofit-adapters/java8/src/main/java/retrofit2/adapter/java8/Java8CallAdapterFactory.java}
 *
 * - Made code style comply with Palantir checkstyle.
 * - Removed the ResponseCallAdapter
 * - Made the BodyCallAdapter create RemoteExceptions.
 */
/**
 * A {@linkplain CallAdapter.Factory call adapter} which creates Java 8 futures.
 * <p>
 * Adding this class to {@link Retrofit} allows you to return {@link CompletableFuture} from
 * service methods.
 * <pre><code>
 * interface MyService {
 *   &#64;GET("user/me")
 *   CompletableFuture&lt;User&gt; getUser()
 * }
 * </code></pre>
 * There are two configurations supported for the {@code CompletableFuture} type parameter:
 * <ul>
 * <li>Direct body (e.g., {@code CompletableFuture<User>}) returns the deserialized body for 2XX
 * responses, sets {@link retrofit2.HttpException HttpException} errors for non-2XX responses, and
 * sets {@link IOException} for network errors.</li>
 * <li>Response wrapped body (e.g., {@code CompletableFuture<Response<User>>}) returns a
 * {@link Response} object for all HTTP responses and sets {@link IOException} for network
 * errors</li>
 * </ul>
 */
public final class AsyncSerializableErrorCallAdapterFactory extends CallAdapter.Factory {
    public static final AsyncSerializableErrorCallAdapterFactory INSTANCE = new AsyncSerializableErrorCallAdapterFactory();

    private AsyncSerializableErrorCallAdapterFactory() {}

    @Override
    public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
        if (getRawType(returnType) != CompletableFuture.class) {
            return null;
        }
        if (!(returnType instanceof ParameterizedType)) {
            throw new IllegalStateException("CompletableFuture return type must be parameterized"
                    + " as CompletableFuture<Foo> or CompletableFuture<? extends Foo>");
        }
        Type innerType = getParameterUpperBound(0, (ParameterizedType) returnType);

        if (getRawType(innerType) != Response.class) {
            // Generic type is not Response<T>. Use it for body-only adapter.
            return new BodyCallAdapter(innerType);
        }

        return null;
    }

    private static class BodyCallAdapter<R> implements CallAdapter<R, CompletableFuture<R>> {
        private final Type responseType;

        BodyCallAdapter(Type responseType) {
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
                public void onResponse(Call<R> call, Response<R> response) {
                    future.complete(response.body());
                }

                @Override
                public void onFailure(Call<R> call, Throwable throwable) {
                    if (throwable instanceof RemoteIoException) {
                        RemoteIoException cast = (RemoteIoException) throwable;
                        future.completeExceptionally(cast.getRuntimeExceptionCause());
                    } else {
                        future.completeExceptionally(throwable);
                    }
                }
            });

            return future;
        }
    }
}
