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

package com.palantir.remoting2.jaxrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting2.clients.CipherSuites;
import com.palantir.remoting2.clients.ClientBuilder;
import com.palantir.remoting2.clients.ClientConfig;
import com.palantir.remoting2.config.service.BasicCredentials;
import com.palantir.remoting2.config.service.ProxyConfiguration;
import com.palantir.remoting2.config.ssl.TrustContext;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import com.palantir.remoting2.jaxrs.feignimpl.FailoverFeignTarget;
import com.palantir.remoting2.jaxrs.feignimpl.FeignSerializableErrorErrorDecoder;
import com.palantir.remoting2.jaxrs.feignimpl.GuavaOptionalAwareContract;
import com.palantir.remoting2.jaxrs.feignimpl.Java8OptionalAwareContract;
import com.palantir.remoting2.jaxrs.feignimpl.NeverRetryingBackoffStrategy;
import com.palantir.remoting2.jaxrs.feignimpl.SlashEncodingContract;
import com.palantir.remoting2.jaxrs.feignimpl.UserAgentInterceptor;
import com.palantir.remoting2.tracing.okhttp3.OkhttpTraceInterceptor;
import feign.CborDelegateDecoder;
import feign.CborDelegateEncoder;
import feign.Contract;
import feign.Feign;
import feign.GuavaOptionalAwareDecoder;
import feign.InputStreamDelegateDecoder;
import feign.InputStreamDelegateEncoder;
import feign.Java8OptionalAwareDecoder;
import feign.Logger;
import feign.Request;
import feign.TextDelegateDecoder;
import feign.TextDelegateEncoder;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JaxRsWithHeaderAndQueryMapContract;
import feign.okhttp.OkHttpClient;
import feign.slf4j.Slf4jLogger;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.TlsVersion;

public final class FeignJaxRsClientBuilder extends ClientBuilder {

    private static final ObjectMapper JSON_OBJECT_MAPPER = ObjectMappers.newClientObjectMapper();
    private static final ObjectMapper CBOR_OBJECT_MAPPER = ObjectMappers.newCborClientObjectMapper();

    private final ClientConfig config;

    FeignJaxRsClientBuilder(ClientConfig config) {
        this.config = config;
        Preconditions.checkArgument(config.maxNumRetries() == 0,
                "Connection-level retries are not supported by %s", JaxRsClient.class.getSimpleName());
    }

    @Override
    public <T> T build(Class<T> serviceClass, String userAgent, List<String> uris) {
        FailoverFeignTarget<T> target = createTarget(serviceClass, uris);
        return Feign.builder()
                .contract(createContract())
                .encoder(
                        new InputStreamDelegateEncoder(
                                new TextDelegateEncoder(
                                        new CborDelegateEncoder(
                                                CBOR_OBJECT_MAPPER,
                                                new JacksonEncoder(JSON_OBJECT_MAPPER)))))
                .decoder(createDecoder(JSON_OBJECT_MAPPER, CBOR_OBJECT_MAPPER))
                .errorDecoder(FeignSerializableErrorErrorDecoder.INSTANCE)
                .client(target.wrapClient(createOkHttpClient()))
                .retryer(target)
                .options(createRequestOptions())
                .logger(new Slf4jLogger(JaxRsClient.class))
                .logLevel(Logger.Level.BASIC)
                .requestInterceptor(UserAgentInterceptor.of(userAgent))
                .target(target);
    }

    private <T> FailoverFeignTarget<T> createTarget(Class<T> serviceClass, List<String> uris) {
        return new FailoverFeignTarget<>(uris, serviceClass, NeverRetryingBackoffStrategy.INSTANCE);
    }

    private Contract createContract() {
        return new SlashEncodingContract(
                new Java8OptionalAwareContract(
                        new GuavaOptionalAwareContract(
                                new JaxRsWithHeaderAndQueryMapContract())));
    }

    private Request.Options createRequestOptions() {
        return new Request.Options(
                (int) config.connectTimeout().toMilliseconds(),
                (int) config.readTimeout().toMilliseconds());
    }

    private static Decoder createDecoder(ObjectMapper objectMapper, ObjectMapper cborObjectMapper) {
        return new Java8OptionalAwareDecoder(
                new GuavaOptionalAwareDecoder(
                        new InputStreamDelegateDecoder(
                                new TextDelegateDecoder(
                                        new CborDelegateDecoder(
                                                cborObjectMapper,
                                                new JacksonDecoder(objectMapper))))));
    }

    private feign.Client createOkHttpClient() {
        okhttp3.OkHttpClient.Builder client = new okhttp3.OkHttpClient.Builder();

        // SSL
        if (config.trustContext().isPresent()) {
            TrustContext context = config.trustContext().get();
            client.sslSocketFactory(context.sslSocketFactory(), context.x509TrustManager());
        }

        // tracing
        client.interceptors().add(OkhttpTraceInterceptor.INSTANCE);

        // timeouts
        // Note that Feign overrides OkHttp timeouts with the timeouts given in FeignBuilder#Options if given, or
        // with its own default otherwise. Feign does not provide a mechanism for write timeouts. We thus need to set
        // write timeouts here and connect&read timeouts on FeignBuilder.
        client.writeTimeout(config.writeTimeout().toMilliseconds(), TimeUnit.MILLISECONDS);

        // Set up HTTP proxy configuration
        if (config.proxy().isPresent()) {
            ProxyConfiguration proxy = config.proxy().get();
            client.proxy(proxy.toProxy());

            if (proxy.credentials().isPresent()) {
                BasicCredentials basicCreds = proxy.credentials().get();
                final String credentials = Credentials.basic(basicCreds.username(), basicCreds.password());
                client.proxyAuthenticator((route, response) -> response.request().newBuilder()
                        .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                        .build());
            }
        }

        // cipher setup
        client.connectionSpecs(createConnectionSpecs(config.enableGcmCipherSuites()));

        // increase default connection pool from 5 @ 5 minutes to 100 @ 10 minutes
        client.connectionPool(new ConnectionPool(100, 10, TimeUnit.MINUTES));

        return new OkHttpClient(client.build());
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
