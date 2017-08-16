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

package com.palantir.remoting3.retrofit2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting.api.config.service.BasicCredentials;
import com.palantir.remoting3.clients.CipherSuites;
import com.palantir.remoting3.clients.ClientConfiguration;
import com.palantir.remoting3.ext.jackson.ObjectMappers;
import com.palantir.remoting3.tracing.Tracers;
import com.palantir.remoting3.tracing.okhttp3.OkhttpTraceInterceptor;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import okhttp3.internal.Util;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public final class Retrofit2ClientBuilder {
    private static final ExecutorService executorService = Tracers.wrap(new Dispatcher().executorService());

    private static final ObjectMapper CBOR_OBJECT_MAPPER = ObjectMappers.newCborClientObjectMapper();
    private static final ObjectMapper OBJECT_MAPPER = ObjectMappers.newClientObjectMapper();

    private final ClientConfiguration config;

    public Retrofit2ClientBuilder(ClientConfiguration config) {
        Preconditions.checkArgument(!config.uris().isEmpty(), "Cannot construct retrofit client with empty URI list");
        this.config = config;
    }

    public <T> T build(Class<T> serviceClass, String userAgent) {
        List<String> sanitizedUris = addTrailingSlashes(config.uris());
        okhttp3.OkHttpClient client = createOkHttpClient(userAgent, sanitizedUris);
        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(sanitizedUris.get(0))
                .callFactory(new AsyncCallTagCallFactory(client))
                .addConverterFactory(new CborConverterFactory(
                        JacksonConverterFactory.create(OBJECT_MAPPER),
                        CBOR_OBJECT_MAPPER))
                .addConverterFactory(OptionalObjectToStringConverterFactory.INSTANCE)
                .addCallAdapterFactory(AsyncSerializableErrorCallAdapterFactory.INSTANCE)
                .build();
        return retrofit.create(serviceClass);
    }

    private static List<String> addTrailingSlashes(List<String> uris) {
        return Lists.transform(uris, input -> input.charAt(input.length() - 1) == '/' ? input : input + "/");
    }

    private OkHttpClient createOkHttpClient(String userAgent, List<String> uris) {
        OkHttpClient.Builder client = new OkHttpClient.Builder();

        // SSL
        client.sslSocketFactory(config.sslSocketFactory(), config.trustManager());

        // proxy
        client.proxySelector(config.proxy());
        if (config.proxyCredentials().isPresent()) {
            BasicCredentials basicCreds = config.proxyCredentials().get();
            final String credentials = Credentials.basic(basicCreds.username(), basicCreds.password());
            client.proxyAuthenticator((route, response) -> response.request().newBuilder()
                    .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                    .build());
        }

        // tracing
        client.interceptors().add(OkhttpTraceInterceptor.INSTANCE);

        // timeouts
        client.connectTimeout(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
        client.readTimeout(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS);
        client.writeTimeout(config.writeTimeout().toMillis(), TimeUnit.MILLISECONDS);

        // retry configuration
        if (config.maxNumRetries() > 1) {
            client.addInterceptor(new RetryInterceptor(config.maxNumRetries()));
        }

        client.addInterceptor(MultiServerRetryInterceptor.create(uris));
        client.addInterceptor(UserAgentInterceptor.of(userAgent));
        client.addInterceptor(SerializableErrorInterceptor.INSTANCE);

        // cipher setup
        client.connectionSpecs(createConnectionSpecs(config.enableGcmCipherSuites()));

        // increase default connection pool from 5 @ 5 minutes to 100 @ 10 minutes
        client.connectionPool(new ConnectionPool(100, 10, TimeUnit.MINUTES));

        client.dispatcher(createDispatcher());

        return client.build();
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
}
