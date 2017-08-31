/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.okhttp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting.api.config.service.BasicCredentials;
import com.palantir.remoting3.clients.CipherSuites;
import com.palantir.remoting3.clients.ClientConfiguration;
import com.palantir.remoting3.tracing.Tracers;
import com.palantir.remoting3.tracing.okhttp3.OkhttpTraceInterceptor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.TlsVersion;
import okhttp3.logging.HttpLoggingInterceptor;

public final class OkHttpClients {

    /**
     * The {@link ExecutorService} used for the {@link Dispatcher}s of all OkHttp clients created through this class.
     */
    private static final ExecutorService executorService = Tracers.wrap(new Dispatcher().executorService());

    private OkHttpClients() {}

    /** Creates an OkHttp client from the given {@link ClientConfiguration}. */
    public static OkHttpClient create(ClientConfiguration config, String userAgent, Class<?> serviceClass) {
        return create(
                config,
                userAgent,
                serviceClass,
                // TODO(rfink): Implement and use jittery exponential backoff.
                () -> new AsyncQosIoExceptionHandler(executorService, new NoDelayBackoff(config.maxNumRetries())));
    }

    @VisibleForTesting
    static OkHttpClient create(ClientConfiguration config, String userAgent, Class<?> serviceClass,
            Supplier<QosIoExceptionHandler> handlerFactory) {
        return new QosIoExceptionAwareOkHttpClient(builder(config, userAgent, serviceClass).build(), handlerFactory);
    }

    /**
     * Link {@link #create}, but returns the pre-configured {@link OkHttpClient.Builder}.
     * <p>
     * TODO(rfink): This method should get removed as soon as Feign and Retrofit clients use the same OkHttp clients.
     */
    public static OkHttpClient.Builder builder(ClientConfiguration config, String userAgent, Class<?> serviceClass) {
        okhttp3.OkHttpClient.Builder client = new okhttp3.OkHttpClient.Builder();

        // error handling
        client.addInterceptor(SerializableErrorInterceptor.INSTANCE);
        client.addInterceptor(QosIoExceptionInterceptor.INSTANCE);

        // SSL
        client.sslSocketFactory(config.sslSocketFactory(), config.trustManager());

        // Retry-aware URLs
        client.addInterceptor(MultiServerRetryInterceptor.create(config.uris(), config.maxNumRetries()));

        // tracing
        client.addInterceptor(OkhttpTraceInterceptor.INSTANCE);

        // timeouts
        // Note that Feign overrides OkHttp timeouts with the timeouts given in FeignBuilder#Options if given, or
        // with its own default otherwise.
        client.connectTimeout(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
        client.readTimeout(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS);
        client.writeTimeout(config.writeTimeout().toMillis(), TimeUnit.MILLISECONDS);

        // proxy
        client.proxySelector(config.proxy());
        if (config.proxyCredentials().isPresent()) {
            BasicCredentials basicCreds = config.proxyCredentials().get();
            final String credentials = Credentials.basic(basicCreds.username(), basicCreds.password());
            client.proxyAuthenticator((route, response) -> response.request().newBuilder()
                    .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                    .build());
        }

        // User agent setup
        client.addInterceptor(UserAgentInterceptor.of(userAgent, serviceClass));

        // cipher setup
        client.connectionSpecs(createConnectionSpecs(config.enableGcmCipherSuites()));

        // increase default connection pool from 5 @ 5 minutes to 100 @ 10 minutes
        client.connectionPool(new ConnectionPool(100, 10, TimeUnit.MINUTES));

        // logging
        client.addInterceptor(new HttpLoggingInterceptor(OkhttpSlf4jDebugLogger.INSTANCE));

        // dispatcher with static executor service
        client.dispatcher(createDispatcher());

        return client;
    }

    private static ImmutableList<ConnectionSpec> createConnectionSpecs(boolean enableGcmCipherSuites) {
        return ImmutableList.of(
                new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .cipherSuites(enableGcmCipherSuites
                                ? CipherSuites.allCipherSuites()
                                : CipherSuites.fastCipherSuites())
                        .build(),
                ConnectionSpec.CLEARTEXT);
    }

    private static Dispatcher createDispatcher() {
        Dispatcher dispatcher = new Dispatcher(executorService);
        dispatcher.setMaxRequests(256);
        dispatcher.setMaxRequestsPerHost(256);
        return dispatcher;
    }

    /**
     * An OkHttp client that executes requests, catches all {@link QosIoException}s and passes them to the configured
     * {@link QosIoExceptionHandler}.
     * <p>
     * See {@link QosIoExceptionHandler} for an end-to-end explanation of http-remoting specific client-side error
     * handling.
     */
    private static final class QosIoExceptionAwareOkHttpClient extends ForwardingOkHttpClient {

        private final Supplier<QosIoExceptionHandler> handlerFactory;

        private QosIoExceptionAwareOkHttpClient(OkHttpClient delegate, Supplier<QosIoExceptionHandler> handlerFactory) {
            super(delegate);
            this.handlerFactory = handlerFactory;
        }

        @Override
        public Call newCall(Request request) {
            return new QosIoExceptionAwareCall(getDelegate().newCall(request), handlerFactory.get());
        }
    }
}
