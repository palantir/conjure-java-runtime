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

package com.palantir.remoting1.retrofit2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting1.clients.ClientBuilder;
import com.palantir.remoting1.clients.ClientConfig;
import com.palantir.remoting1.config.service.BasicCredentials;
import com.palantir.remoting1.config.service.ProxyConfiguration;
import com.palantir.remoting1.config.ssl.TrustContext;
import com.palantir.remoting1.ext.jackson.ObjectMappers;
import com.palantir.remoting1.tracing.okhttp3.OkhttpTraceInterceptor;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Authenticator;
import okhttp3.CipherSuite;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.TlsVersion;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public final class Retrofit2ClientBuilder extends ClientBuilder {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMappers.guavaJdk8();

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
                .build();
        return retrofit.create(serviceClass);
    }

    private static List<String> addTrailingSlashes(List<String> uris) {
        return Lists.transform(uris, new Function<String, String>() {
            @Override
            public String apply(String input) {
                return input.charAt(input.length() - 1) == '/'
                        ? input
                        : input + "/";
            }
        });
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
                client.proxyAuthenticator(new Authenticator() {
                    @Override
                    public Request authenticate(Route route, Response response) throws IOException {
                        return response.request().newBuilder()
                                .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                                .build();
                    }
                });
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
        client.connectionSpecs(CONNECTION_SPEC);

        // increase default connection pool from 5 @ 5 minutes to 100 @ 10 minutes
        client.connectionPool(new ConnectionPool(100, 10, TimeUnit.MINUTES));

        return client.build();
    }
}
