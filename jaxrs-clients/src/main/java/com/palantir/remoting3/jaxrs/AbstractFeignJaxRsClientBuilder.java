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

package com.palantir.remoting3.jaxrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting.api.config.service.BasicCredentials;
import com.palantir.remoting3.clients.CipherSuites;
import com.palantir.remoting3.clients.ClientConfiguration;
import com.palantir.remoting3.jaxrs.feignimpl.FeignSerializableErrorErrorDecoder;
import com.palantir.remoting3.jaxrs.feignimpl.GuavaOptionalAwareContract;
import com.palantir.remoting3.jaxrs.feignimpl.Java8OptionalAwareContract;
import com.palantir.remoting3.jaxrs.feignimpl.SlashEncodingContract;
import com.palantir.remoting3.okhttp.MultiServerRetryInterceptor;
import com.palantir.remoting3.okhttp.OkhttpSlf4jDebugLogger;
import com.palantir.remoting3.okhttp.UserAgentInterceptor;
import com.palantir.remoting3.tracing.okhttp3.OkhttpTraceInterceptor;
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
import feign.Retryer;
import feign.TextDelegateDecoder;
import feign.TextDelegateEncoder;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JAXRSContract;
import feign.okhttp.OkHttpClient;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.TlsVersion;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Not meant to be implemented outside of this library.
 */
abstract class AbstractFeignJaxRsClientBuilder {

    private final ClientConfiguration config;
    /**
     * The primary URI used to bootstrap the Feign client; this is the URI used by Feign to create an OkHttp call. Note
     * that the {@link MultiServerRetryInterceptor} replaces this URI with a random URI from the client configuration
     * before making the request.
     */
    private final String primaryUri;

    AbstractFeignJaxRsClientBuilder(ClientConfiguration config) {
        Preconditions.checkArgument(config.maxNumRetries() == 0,
                "Connection-level retries are not supported by %s", JaxRsClient.class.getSimpleName());
        Preconditions.checkArgument(!config.uris().isEmpty(), "Must provide at least one service URI");
        this.config = config;
        this.primaryUri = config.uris().get(0);
    }

    protected abstract ObjectMapper getObjectMapper();

    protected abstract ObjectMapper getCborObjectMapper();

    public final <T> T build(Class<T> serviceClass, String userAgent) {
        ObjectMapper objectMapper = getObjectMapper();
        ObjectMapper cborObjectMapper = getCborObjectMapper();

        return Feign.builder()
                .contract(createContract())
                .encoder(
                        new InputStreamDelegateEncoder(
                                new TextDelegateEncoder(
                                        new CborDelegateEncoder(
                                                cborObjectMapper,
                                                new JacksonEncoder(objectMapper)))))
                .decoder(createDecoder(objectMapper, cborObjectMapper))
                .errorDecoder(FeignSerializableErrorErrorDecoder.INSTANCE)
                .client(createOkHttpClient(userAgent, config.uris()))
                .options(createRequestOptions())
                .logLevel(Logger.Level.NONE)  // we use OkHttp interceptors for logging. (note that NONE is the default)
                .retryer(new Retryer.Default(0, 0, 1))  // use OkHttp retry mechanism only
                .target(serviceClass, primaryUri);
    }

    private Contract createContract() {
        return new SlashEncodingContract(
                new Java8OptionalAwareContract(
                        new GuavaOptionalAwareContract(
                                new JAXRSContract())));
    }

    private Request.Options createRequestOptions() {
        return new Request.Options(
                Math.toIntExact(config.connectTimeout().toMillis()),
                Math.toIntExact(config.readTimeout().toMillis()));
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

    private feign.Client createOkHttpClient(String userAgent, List<String> uris) {
        okhttp3.OkHttpClient.Builder client = new okhttp3.OkHttpClient.Builder();

        // SSL
        client.sslSocketFactory(config.sslSocketFactory(), config.trustManager());

        // Retry-aware URLs
        client.addInterceptor(MultiServerRetryInterceptor.create(uris, true));

        // tracing
        client.addInterceptor(OkhttpTraceInterceptor.INSTANCE);

        // timeouts
        // Note that Feign overrides OkHttp timeouts with the timeouts given in FeignBuilder#Options if given, or
        // with its own default otherwise. Feign does not provide a mechanism for write timeouts. We thus need to set
        // write timeouts here and connect&read timeouts on FeignBuilder.
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
        client.addInterceptor(UserAgentInterceptor.of(userAgent));

        // cipher setup
        client.connectionSpecs(createConnectionSpecs(config.enableGcmCipherSuites()));

        // increase default connection pool from 5 @ 5 minutes to 100 @ 10 minutes
        client.connectionPool(new ConnectionPool(100, 10, TimeUnit.MINUTES));

        // logging
        client.addInterceptor(new HttpLoggingInterceptor(OkhttpSlf4jDebugLogger.INSTANCE));

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
