/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.jaxrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.jaxrs.feignimpl.GuavaOptionalAwareContract;
import com.palantir.conjure.java.client.jaxrs.feignimpl.Java8OptionalAwareContract;
import com.palantir.conjure.java.client.jaxrs.feignimpl.PathTemplateHeaderEnrichmentContract;
import com.palantir.conjure.java.client.jaxrs.feignimpl.PathTemplateHeaderRewriter;
import com.palantir.conjure.java.client.jaxrs.feignimpl.SlashEncodingContract;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.conjure.java.okhttp.OkHttpClients;
import com.palantir.logsafe.Preconditions;
import feign.CborDelegateDecoder;
import feign.CborDelegateEncoder;
import feign.CoerceNullCollectionsDecoder;
import feign.Contract;
import feign.Feign;
import feign.GuavaOptionalAwareDecoder;
import feign.InputStreamDelegateDecoder;
import feign.InputStreamDelegateEncoder;
import feign.Java8OptionalAwareDecoder;
import feign.Logger;
import feign.NeverReturnNullDecoder;
import feign.Request;
import feign.Retryer;
import feign.TextDelegateDecoder;
import feign.TextDelegateEncoder;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JAXRSContract;
import feign.okhttp.OkHttpClient;

/**
 * Not meant to be implemented outside of this library.
 */
abstract class AbstractFeignJaxRsClientBuilder {

    private final ClientConfiguration config;

    /**
     * The primary URI used to bootstrap the Feign client; this is the URI used by Feign to create an OkHttp call. Note
     * that this URI is typically replaced in the OkHttp client with a random URI from the client configuration when
     * retrying a request.
     */
    private final String primaryUri;

    private HostMetricsRegistry hostMetricsRegistry;

    AbstractFeignJaxRsClientBuilder(ClientConfiguration config) {
        Preconditions.checkArgument(!config.uris().isEmpty(), "Must provide at least one service URI");
        this.config = config;
        this.primaryUri = config.uris().get(0);
    }

    protected abstract ObjectMapper getObjectMapper();

    protected abstract ObjectMapper getCborObjectMapper();

    /**
     * Set the host metrics registry to use when constructing the OkHttp client.
     */
    public final AbstractFeignJaxRsClientBuilder hostMetricsRegistry(HostMetricsRegistry newHostMetricsRegistry) {
        Preconditions.checkNotNull(newHostMetricsRegistry, "hostMetricsRegistry can't be null");
        hostMetricsRegistry = newHostMetricsRegistry;
        return this;
    }

    public final <T> T build(Class<T> serviceClass, UserAgent userAgent) {
        ObjectMapper objectMapper = getObjectMapper();
        ObjectMapper cborObjectMapper = getCborObjectMapper();
        Preconditions.checkNotNull(hostMetricsRegistry, "hostMetricsRegistry must be set");
        okhttp3.OkHttpClient okHttpClient = OkHttpClients.create(config, userAgent, hostMetricsRegistry, serviceClass);

        return Feign.builder()
                .contract(createContract())
                .encoder(
                        new InputStreamDelegateEncoder(
                                new TextDelegateEncoder(
                                        new CborDelegateEncoder(
                                                cborObjectMapper,
                                                new JacksonEncoder(objectMapper)))))
                .decoder(createDecoder(objectMapper, cborObjectMapper))
                .requestInterceptor(PathTemplateHeaderRewriter.INSTANCE)
                .client(new OkHttpClient(okHttpClient))
                .options(createRequestOptions())
                .logLevel(Logger.Level.NONE)  // we use OkHttp interceptors for logging. (note that NONE is the default)
                .retryer(new Retryer.Default(0, 0, 1))  // use OkHttp retry mechanism only
                .target(serviceClass, primaryUri);
    }

    private Contract createContract() {
        return new PathTemplateHeaderEnrichmentContract(
                new SlashEncodingContract(
                        new Java8OptionalAwareContract(
                                new GuavaOptionalAwareContract(
                                        new JAXRSContract()))));
    }

    private Request.Options createRequestOptions() {
        return new Request.Options(
                Math.toIntExact(config.connectTimeout().toMillis()),
                Math.toIntExact(config.readTimeout().toMillis()));
    }

    private static Decoder createDecoder(ObjectMapper objectMapper, ObjectMapper cborObjectMapper) {
        return new NeverReturnNullDecoder(
                new CoerceNullCollectionsDecoder(
                    new Java8OptionalAwareDecoder(
                        new GuavaOptionalAwareDecoder(
                                new InputStreamDelegateDecoder(
                                        new TextDelegateDecoder(
                                                new CborDelegateDecoder(
                                                        cborObjectMapper,
                                                            new JacksonDecoder(objectMapper))))))));
    }
}
