/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.retrofit2;

import com.palantir.conjure.java.QosExceptionResponseMapper;
import com.palantir.conjure.java.api.errors.QosException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

final class QosExceptionThrowingCallAdapterFactory extends CallAdapter.Factory {

    private final CallAdapter.Factory delegate;

    QosExceptionThrowingCallAdapterFactory(CallAdapter.Factory delegate) {
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
        CallAdapter<?, ?> adapter = delegate.get(returnType, annotations, retrofit);
        return new QosExceptionThrowingCallAdapter<>(adapter);
    }

    private static final class QosExceptionThrowingCallAdapter<R, T> implements CallAdapter<R, T> {

        private final CallAdapter<R, T> delegate;

        private QosExceptionThrowingCallAdapter(CallAdapter<R, T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Type responseType() {
            return delegate.responseType();
        }

        @Override
        public T adapt(Call<R> call) {
            QosExceptionThrowingCall<R> throwingCall = new QosExceptionThrowingCall(call);
            return delegate.adapt(throwingCall);
        }
    }

    private static final class QosExceptionThrowingCall<R> implements Call<R> {
        private final Call<R> delegate;

        private QosExceptionThrowingCall(Call<R> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Response<R> execute() throws IOException {
            Response<R> response = delegate.execute();
            Map<String, List<String>> headers = response.headers().toMultimap();
            Optional<QosException> exception = QosExceptionResponseMapper.mapResponseCodeHeaderStream(
                    response.code(),
                    header -> Optional.ofNullable(headers.get(header)).map(List::stream).orElseGet(Stream::empty));
            if (exception.isPresent()) {
                throw exception.get();
            }
            return response;
        }

        @Override
        public void enqueue(Callback<R> callback) {
            delegate.enqueue(callback);
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

        @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
        @Override
        public Call<R> clone() {
            return new QosExceptionThrowingCall<>(delegate.clone());
        }

        @Override
        public Request request() {
            return delegate.request();
        }
    }
}
