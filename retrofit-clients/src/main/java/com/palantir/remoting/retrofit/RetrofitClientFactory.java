/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.retrofit;

import com.google.common.base.Optional;
import com.palantir.remoting.retrofit.errors.RetrofitSerializableErrorErrorHandler;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.OkClient;

/**
 * Utilities to help create Retrofit proxies. Feign clients should be preferred except in cases where proxies must
 * support file upload and download. Read and write timeouts are customizable in order to allow arbitrary sized file
 * uploads/downloads.
 */
public final class RetrofitClientFactory {

    private static final Interceptor TRACE_INTERCEPTOR = new TraceInterceptor();

    private RetrofitClientFactory() {}

    private static Client newHttpClient(Optional<SSLSocketFactory> sslSocketFactory, OkHttpClientOptions options) {
        OkHttpClient okClient = new OkHttpClient();

        // timeouts
        okClient.setConnectTimeout(
                options.getConnectTimeoutMs().or((long) okClient.getConnectTimeout()), TimeUnit.MILLISECONDS);
        okClient.setReadTimeout(options.getReadTimeoutMs().or((long) okClient.getReadTimeout()), TimeUnit.MILLISECONDS);
        okClient.setWriteTimeout(
                options.getWriteTimeoutMs().or((long) okClient.getWriteTimeout()), TimeUnit.MILLISECONDS);

        // tracing
        okClient.interceptors().add(TRACE_INTERCEPTOR);

        // retries
        RetryInterceptor retryInterceptor = options.getMaxNumberRetries().isPresent()
                ? new RetryInterceptor(options.getMaxNumberRetries().get())
                : new RetryInterceptor();
        okClient.interceptors().add(retryInterceptor);

        // ssl
        okClient.setSslSocketFactory(sslSocketFactory.orNull());

        return new OkClient(okClient);
    }

    public static <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactoryOptional, String uri, Class<T> type,
            OkHttpClientOptions options) {
        Client client = newHttpClient(sslSocketFactoryOptional, options);
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(uri)
                .setClient(client)
                .setErrorHandler(RetrofitSerializableErrorErrorHandler.INSTANCE)
                .build();
        return restAdapter.create(type);
    }
}
