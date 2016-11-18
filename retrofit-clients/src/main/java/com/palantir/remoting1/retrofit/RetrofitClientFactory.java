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

package com.palantir.remoting1.retrofit;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.palantir.remoting1.retrofit.errors.RetrofitSerializableErrorErrorHandler;
import com.palantir.remoting1.tracing.okhttp.OkhttpTraceInterceptor;
import com.squareup.okhttp.CipherSuite;
import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.TlsVersion;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.OkClient;

/**
 * Utilities to help create Retrofit proxies. Feign clients should be preferred except in cases where proxies must
 * support file upload and download. Read and write timeouts are customizable in order to allow arbitrary sized file
 * uploads/downloads.
 * <p>
 * All factories take a User Agent and this will be embedded as the User Agent header for all requests.
 * For services, recommended user agents are of the form: {@code ServiceName (Version)}, e.g. MyServer (1.2.3)
 * For services that run multiple instances, recommended user agents are of the form:
 * {@code ServiceName/InstanceId (Version)}, e.g. MyServer/12 (1.2.3)
 * <p>
 * @deprecated The retrofit-clients project uses Retrofit 1.x and is considered deprecated. This class will be removed
 * in a future release.
 */
@Deprecated
public final class RetrofitClientFactory {

    private static final ImmutableList<ConnectionSpec> CONNECTION_SPEC = ImmutableList.of(
            new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .cipherSuites(
                            // In an ideal world, we'd use GCM suites, but they're an order of
                            // magnitude slower than the CBC suites, which have JVM optimizations
                            // already. We should revisit with JDK9.
                            // See also:
                            //  - http://openjdk.java.net/jeps/246
                            //  - https://bugs.openjdk.java.net/secure/attachment/25422/GCM%20Analysis.pdf
                            // CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                            // CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                            // CipherSuite.TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384,
                            // CipherSuite.TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256,
                            // CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
                            // CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
                            CipherSuite.TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384,
                            CipherSuite.TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256,
                            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
                            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_ECDH_RSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_ECDH_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV)
                    .build(),
                    ConnectionSpec.CLEARTEXT);

    private RetrofitClientFactory() {}

    private static Client newHttpClient(Optional<SSLSocketFactory> sslSocketFactory, OkHttpClientOptions options,
            String userAgent) {
        OkHttpClient okClient = new OkHttpClient();

        // timeouts
        okClient.setConnectTimeout(
                options.getConnectTimeoutMs().or((long) okClient.getConnectTimeout()), TimeUnit.MILLISECONDS);
        okClient.setReadTimeout(options.getReadTimeoutMs().or((long) okClient.getReadTimeout()), TimeUnit.MILLISECONDS);
        okClient.setWriteTimeout(
                options.getWriteTimeoutMs().or((long) okClient.getWriteTimeout()), TimeUnit.MILLISECONDS);

        // tracing
        okClient.interceptors().add(OkhttpTraceInterceptor.INSTANCE);

        // retries
        RetryInterceptor retryInterceptor = options.getMaxNumberRetries().isPresent()
                ? new RetryInterceptor(options.getMaxNumberRetries().get())
                : new RetryInterceptor();

        okClient.interceptors().add(retryInterceptor);
        okClient.interceptors().add(UserAgentInterceptor.of(userAgent));

        // ssl
        okClient.setSslSocketFactory(sslSocketFactory.orNull());

        // cipher setup
        okClient.setConnectionSpecs(CONNECTION_SPEC);

        // increase default connection pool from 5 @ 5 minutes to 100 @ 10 minutes
        okClient.setConnectionPool(new ConnectionPool(100, 10, TimeUnit.MINUTES));

        return new OkClient(okClient);
    }

    /**
     * @deprecated Clients should specify a user agent. This method will be removed when clients have updated.
     */
    @Deprecated
    public static <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactoryOptional, String uri, Class<T> type,
            OkHttpClientOptions options) {
        return createProxy(sslSocketFactoryOptional, uri, type, options, "UnspecifiedUserAgent");
    }

    public static <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactoryOptional, String uri, Class<T> type,
            OkHttpClientOptions options, String userAgent) {
        Client client = newHttpClient(sslSocketFactoryOptional, options, userAgent);
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(uri)
                .setClient(client)
                .setErrorHandler(RetrofitSerializableErrorErrorHandler.INSTANCE)
                .build();
        return restAdapter.create(type);
    }
}
