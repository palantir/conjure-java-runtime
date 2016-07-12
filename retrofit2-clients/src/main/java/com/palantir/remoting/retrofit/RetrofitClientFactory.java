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
import com.google.common.net.HttpHeaders;
import com.palantir.config.service.BasicCredentials;
import com.palantir.config.service.proxy.DefaultProxyConfigurationProviderChain;
import com.palantir.config.service.proxy.ProxyConfiguration;
import com.palantir.config.service.proxy.ProxyConfigurationProvider;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Utilities to help create Retrofit proxies. Feign clients should be preferred except in cases where proxies must
 * support file upload and download. Read and write timeouts are customizable in order to allow arbitrary sized file
 * uploads/downloads.
 * <p>
 * All factories take a User Agent and this will be embedded as the User Agent header for all requests. For services,
 * recommended user agents are of the form: {@code ServiceName (Version)}, e.g. MyServer (1.2.3) For services that run
 * multiple instances, recommended user agents are of the form: {@code ServiceName/InstanceId (Version)}, e.g.
 * MyServer/12 (1.2.3)
 */
public final class RetrofitClientFactory {

    private RetrofitClientFactory() {}

    private static OkHttpClient newHttpClient(
            Optional<SSLSocketFactory> sslSocketFactory, OkHttpClientOptions options, String userAgent,
            ProxyConfigurationProvider proxyConfigurationProvider) {

        OkHttpClient.Builder okClient = new OkHttpClient.Builder();

        // timeouts
        if (options.getConnectTimeoutMs().isPresent()) {
            okClient.connectTimeout(options.getConnectTimeoutMs().get(), TimeUnit.MILLISECONDS);
        }
        if (options.getReadTimeoutMs().isPresent()) {
            okClient.readTimeout(options.getReadTimeoutMs().get(), TimeUnit.MILLISECONDS);
        }
        if (options.getWriteTimeoutMs().isPresent()) {
            okClient.writeTimeout(options.getWriteTimeoutMs().get(), TimeUnit.MILLISECONDS);
        }

        // SSL configuration
        if (sslSocketFactory.isPresent()) {
            // TODO(rfink) OkHttp would prefer to also be given a X509TrustManager. Where can we get one?
            okClient.sslSocketFactory(sslSocketFactory.get());
        }

        Optional<ProxyConfiguration> proxyConfiguration = proxyConfigurationProvider.getProxyConfiguration();
        if (proxyConfiguration.isPresent()) {
            ProxyConfiguration proxy = proxyConfiguration.get();
            okClient.proxy(proxy.toProxy());

            if (proxy.credentials().isPresent()) {
                BasicCredentials basicCreds = proxy.credentials().get();
                final String credentials = Credentials.basic(basicCreds.username(), basicCreds.password());
                okClient.proxyAuthenticator(new Authenticator() {
                    @Override
                    public okhttp3.Request authenticate(Route route, Response response) throws IOException {
                        return response.request().newBuilder()
                                .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                                .build();
                    }
                });
            }
        }

        // retry configuration
        if (options.getMaxNumberRetries().isPresent()) {
            okClient.addInterceptor(new RetryInterceptor(options.getMaxNumberRetries().get()));
        }

        okClient.addInterceptor(UserAgentInterceptor.of(userAgent));
        okClient.addInterceptor(SerializableErrorInterceptor.INSTANCE);

        return okClient.build();
    }

    public static <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactoryOptional, String uri, Class<T> type,
            OkHttpClientOptions options, String userAgent) {
        return createProxy(sslSocketFactoryOptional, uri, type, options, userAgent,
                new DefaultProxyConfigurationProviderChain());
    }

    public static <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactoryOptional, String uri, Class<T> type,
                                    OkHttpClientOptions options, String userAgent,
                                    ProxyConfigurationProvider proxyConfigurationProvider) {
        okhttp3.OkHttpClient client = newHttpClient(sslSocketFactoryOptional, options, userAgent,
                proxyConfigurationProvider);
        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(uri)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(type);
    }
}
