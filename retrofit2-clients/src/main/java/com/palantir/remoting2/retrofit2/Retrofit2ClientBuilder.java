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

package com.palantir.remoting2.retrofit2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting2.clients.CipherSuites;
import com.palantir.remoting2.clients.ClientBuilder;
import com.palantir.remoting2.clients.ClientConfig;
import com.palantir.remoting2.config.service.BasicCredentials;
import com.palantir.remoting2.config.service.ProxyConfiguration;
import com.palantir.remoting2.config.ssl.TrustContext;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import com.palantir.remoting2.http2.Http2Agent;
import com.palantir.remoting2.tracing.okhttp3.OkhttpTraceInterceptor;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public final class Retrofit2ClientBuilder extends ClientBuilder {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMappers.newClientObjectMapper();

    private final ClientConfig config;

    public Retrofit2ClientBuilder() {
        this.config = ClientConfig.builder().build();
    }

    public Retrofit2ClientBuilder(ClientConfig config) {
        this.config = config;
    }

    @Override
    public <T> T build(Class<T> serviceClass, String userAgent, List<String> uris) {
        Preconditions.checkArgument(!uris.isEmpty());
        List<String> sanitizedUris = addTrailingSlashes(uris);
        okhttp3.OkHttpClient client = createOkHttpClient(userAgent, sanitizedUris);
        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(sanitizedUris.get(0))
                .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
                .addConverterFactory(OptionalObjectToStringConverterFactory.INSTANCE)
                .build();
        return retrofit.create(serviceClass);
    }

    private static List<String> addTrailingSlashes(List<String> uris) {
        return Lists.transform(uris, input -> input.charAt(input.length() - 1) == '/' ? input : input + "/");
    }

    private OkHttpClient createOkHttpClient(String userAgent, List<String> uris) {

        OkHttpClient.Builder client = new OkHttpClient.Builder();

        // SSL
        if (config.trustContext().isPresent()) {
            TrustContext context = config.trustContext().get();
            client.sslSocketFactory(context.sslSocketFactory(), context.x509TrustManager());
        }

        // proxy
        if (config.proxy().isPresent()) {
            ProxyConfiguration proxy = config.proxy().get();
            client.proxy(proxy.toProxy());

            if (proxy.credentials().isPresent()) {
                BasicCredentials proxyCredentials = proxy.credentials().get();
                final String credentials = Credentials.basic(proxyCredentials.username(), proxyCredentials.password());
                client.proxyAuthenticator((route, response) -> response.request().newBuilder()
                        .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                        .build());
            }
        }

        // tracing
        client.interceptors().add(OkhttpTraceInterceptor.INSTANCE);

        // timeouts
        client.connectTimeout(config.connectTimeout().toMilliseconds(), TimeUnit.MILLISECONDS);
        client.readTimeout(config.readTimeout().toMilliseconds(), TimeUnit.MILLISECONDS);
        client.writeTimeout(config.writeTimeout().toMilliseconds(), TimeUnit.MILLISECONDS);

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
}
