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
import com.palantir.remoting3.clients.ClientConfiguration;
import com.palantir.remoting3.jaxrs.feignimpl.GuavaOptionalAwareContract;
import com.palantir.remoting3.jaxrs.feignimpl.Java8OptionalAwareContract;
import com.palantir.remoting3.jaxrs.feignimpl.SlashEncodingContract;
import com.palantir.remoting3.okhttp.MultiServerRetryInterceptor;
import com.palantir.remoting3.okhttp.OkHttpClients;
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

/**
 * Not meant to be implemented outside of this library.
 */
abstract class AbstractFeignJaxRsClientBuilder {

    private final ClientConfiguration config;
    /**
     * The primary URI used to bootstrap the Feign client; this is the URI used by Feign to create an OkHttp call. Note
     * that the {@link MultiServerRetryInterceptor} replaces this URI with a random URI from the client configuration
     * when retrying a request.
     */
    private final String primaryUri;

    AbstractFeignJaxRsClientBuilder(ClientConfiguration config) {
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
                .client(new OkHttpClient(OkHttpClients.create(config, userAgent, serviceClass)))
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
}
