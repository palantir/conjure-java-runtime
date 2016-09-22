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

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.okhttp.BraveOkHttpRequestResponseInterceptor;
import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting1.clients.ClientBuilder;
import com.palantir.remoting1.clients.ClientConfig;
import com.palantir.remoting1.config.service.BasicCredentials;
import com.palantir.remoting1.config.service.ProxyConfiguration;
import com.palantir.remoting1.config.ssl.TrustContext;
import com.palantir.remoting1.servers.BraveTracer;
import com.palantir.remoting1.servers.Tracer;
import com.palantir.remoting1.servers.Tracers;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public final class Retrofit2ClientBuilder extends ClientBuilder {

    private static final Logger logger = LoggerFactory.getLogger(Retrofit2ClientBuilder.class);

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
        okhttp3.OkHttpClient client = createOkHttpClient(userAgent, uris);
        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(uris.get(0))
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
        return retrofit.create(serviceClass);
    }

    private OkHttpClient createOkHttpClient(String userAgent, List<String> uris) {

        OkHttpClient.Builder client = new OkHttpClient.Builder();

        // SSL
        if (config.trustContext().isPresent()) {
            TrustContext context = config.trustContext().get();
            client.sslSocketFactory(context.sslSocketFactory(), context.x509TrustManager());
        }

        setupZipkinBraveTracing(userAgent, client);

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

        return client.build();
    }

    private void setupZipkinBraveTracing(final String userAgent, okhttp3.OkHttpClient.Builder client) {
        Tracer tracer = getTracer();
        if (!(tracer instanceof BraveTracer)) {
            // TODO (davids) getOrCreate Brave and set tracer
            logger.warn("Zipkin tracing for '{}' is not enabled due to incompatible tracer {}", userAgent, tracer);
            return;
        }

        // TODO (davids) include user agent annotation in request span annotation
        final ClientTracer clientTracer = ((BraveTracer) tracer).getBrave().clientTracer();
        client.addInterceptor(
                new BraveOkHttpRequestResponseInterceptor(
                        new ClientRequestInterceptor(clientTracer),
                        new ClientResponseInterceptor(clientTracer),
                        new DefaultSpanNameProvider()));
    }

    private Tracer getTracer() {
        Tracer tracer = config.tracer();
        Tracer activeTracer = Tracers.activeTracer();
        if (!tracer.equals(activeTracer)) {
            logger.warn("Found different tracers: config: {}, active: {}", tracer, activeTracer);
        }
        return tracer;
    }
}
