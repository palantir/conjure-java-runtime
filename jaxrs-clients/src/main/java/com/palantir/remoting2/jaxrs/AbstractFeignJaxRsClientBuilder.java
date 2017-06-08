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

package com.palantir.remoting2.jaxrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.palantir.remoting2.clients.ClientBuilder;
import com.palantir.remoting2.clients.ClientConfig;
import com.palantir.remoting2.jaxrs.feignimpl.FailoverFeignTarget;
import com.palantir.remoting2.jaxrs.feignimpl.FeignSerializableErrorErrorDecoder;
import com.palantir.remoting2.jaxrs.feignimpl.GuavaOptionalAwareContract;
import com.palantir.remoting2.jaxrs.feignimpl.Java8OptionalAwareContract;
import com.palantir.remoting2.jaxrs.feignimpl.NeverRetryingBackoffStrategy;
import com.palantir.remoting2.jaxrs.feignimpl.SlashEncodingContract;
import com.palantir.remoting2.jaxrs.feignimpl.UserAgentInterceptor;
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

/**
 * Not meant to be implemented outside of this library.
 */
abstract class AbstractFeignJaxRsClientBuilder extends ClientBuilder {

    private final ClientConfig config;

    AbstractFeignJaxRsClientBuilder(ClientConfig config) {
        this.config = config;
        Preconditions.checkArgument(config.maxNumRetries() == 0,
                "Connection-level retries are not supported by %s", JaxRsClient.class.getSimpleName());
    }

    protected abstract ObjectMapper getObjectMapper();

    protected abstract ObjectMapper getCborObjectMapper();

    @Override
    public final <T> T build(Class<T> serviceClass, String userAgent, List<String> uris) {
        FailoverFeignTarget<T> target = createTarget(serviceClass, uris);
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
        return new OkHttpClient(OkHttpClients.createClient(config));
    }
}
