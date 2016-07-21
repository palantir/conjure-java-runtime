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

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;
import com.palantir.config.service.BasicCredentials;
import com.palantir.config.service.ProxyConfiguration;
import com.palantir.remoting.clients.ClientBuilder;
import com.palantir.remoting.clients.ClientConfig;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public final class Retrofit2ClientBuilder extends ClientBuilder {

    private final ClientConfig config;

    public Retrofit2ClientBuilder(ClientConfig config) {
        this.config = config;
    }

    @Override
    public <T> T build(Class<T> serviceClass, String userAgent, List<String> uris) {
        Preconditions.checkArgument(uris.size() == 1,
                "%s support single URI targets only", Retrofit2Client.class.getSimpleName());
        String uri = Iterables.getOnlyElement(uris);
        okhttp3.OkHttpClient client = createOkHttpClient(userAgent);
        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(uri)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
        return retrofit.create(serviceClass);
    }

    private OkHttpClient createOkHttpClient(String userAgent) {

        OkHttpClient.Builder client = new OkHttpClient.Builder();

        // SSL
        if (config.getSslSocketFactory().isPresent()) {
            Preconditions.checkArgument(config.getX509TrustManager().isPresent(),
                    "Internal error: ClientConfig provided SslSocketFactory, but no X509TrustManager");
            client.sslSocketFactory(config.getSslSocketFactory().get(), config.getX509TrustManager().get());
        }

        // proxy
        if (config.getProxyConfiguration().isPresent()) {
            ProxyConfiguration proxy = config.getProxyConfiguration().get();
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

        // timeouts
        client.connectTimeout(config.getConnectTimeout().getMillis(), TimeUnit.MILLISECONDS);
        client.readTimeout(config.getReadTimeout().getMillis(), TimeUnit.MILLISECONDS);
        client.writeTimeout(config.getWriteTimeout().getMillis(), TimeUnit.MILLISECONDS);

        // retry configuration
        if (config.getMaxNumRetries() > 1) {
            client.addInterceptor(new RetryInterceptor(config.getMaxNumRetries()));
        }

        client.addInterceptor(UserAgentInterceptor.of(userAgent));
        client.addInterceptor(SerializableErrorInterceptor.INSTANCE);

        return client.build();
    }
}
