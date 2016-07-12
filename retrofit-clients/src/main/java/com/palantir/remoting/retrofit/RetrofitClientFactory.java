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
import com.palantir.remoting.retrofit.errors.RetrofitSerializableErrorErrorHandler;
import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.net.Proxy;
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

    private RetrofitClientFactory() {}

    private static Client newHttpClient(Optional<SSLSocketFactory> sslSocketFactory, OkHttpClientOptions options,
            String userAgent, ProxyConfigurationProvider proxyConfigurationProvider) {
        OkHttpClient okClient = new OkHttpClient();

        // timeouts
        okClient.setConnectTimeout(
                options.getConnectTimeoutMs().or((long) okClient.getConnectTimeout()), TimeUnit.MILLISECONDS);
        okClient.setReadTimeout(options.getReadTimeoutMs().or((long) okClient.getReadTimeout()), TimeUnit.MILLISECONDS);
        okClient.setWriteTimeout(
                options.getWriteTimeoutMs().or((long) okClient.getWriteTimeout()), TimeUnit.MILLISECONDS);

        Optional<ProxyConfiguration> proxyConfiguration = proxyConfigurationProvider.getProxyConfiguration();
        if (proxyConfiguration.isPresent()) {
            ProxyConfiguration proxy = proxyConfiguration.get();
            okClient.setProxy(proxy.toProxy());

            if (proxy.credentials().isPresent()) {
                BasicCredentials basicCreds = proxy.credentials().get();
                final String credentials = Credentials.basic(basicCreds.username(), basicCreds.password());
                okClient.setAuthenticator(new Authenticator() {

                    @Override
                    public Request authenticate(Proxy proxy, Response response) throws IOException {
                        return null;
                    }

                    @Override
                    public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
                        return response.request().newBuilder()
                                .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                                .build();
                    }
                });
            }
        }

        // retries
        RetryInterceptor retryInterceptor = options.getMaxNumberRetries().isPresent()
                ? new RetryInterceptor(options.getMaxNumberRetries().get())
                : new RetryInterceptor();

        okClient.interceptors().add(retryInterceptor);
        okClient.interceptors().add(UserAgentInterceptor.of(userAgent));

        // ssl
        okClient.setSslSocketFactory(sslSocketFactory.orNull());

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
        return createProxy(sslSocketFactoryOptional, uri, type, options, userAgent,
                new DefaultProxyConfigurationProviderChain());
    }

    public static <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactoryOptional, String uri, Class<T> type,
                                    OkHttpClientOptions options, String userAgent,
                                    ProxyConfigurationProvider proxyConfigurationProvider) {
        Client client = newHttpClient(sslSocketFactoryOptional, options, userAgent, proxyConfigurationProvider);
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(uri)
                .setClient(client)
                .setErrorHandler(RetrofitSerializableErrorErrorHandler.INSTANCE)
                .build();
        return restAdapter.create(type);
    }
}
