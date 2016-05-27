/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.palantir.remoting.http.errors.FeignSerializableErrorErrorDecoder;
import feign.InputStreamDelegateDecoder;
import feign.InputStreamDelegateEncoder;
import feign.OptionalAwareDecoder;
import feign.Request;
import feign.Request.Options;
import feign.TextDelegateDecoder;
import feign.TextDelegateEncoder;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JaxRsWithHeaderAndQueryMapContract;

/**
 * Static factory methods for producing common configurations of {@link FeignClientFactory}, which in turn may be used
 * to create HTTP proxies for HTTP remoting clients. The returned instances serialize server-side exceptions as JSON and
 * decode any 204 response as an {@link Optional#absent} in case the proxied interface is of type {@link Optional}.
 */
public final class FeignClients {

    private static final Options DEFAULT_TIMEOUT_OPTIONS = new Request.Options();

    private FeignClients() {}

    /**
     * Provides a {@link FeignClientFactory} with an {@link ObjectMapper} configured with {@link
     * com.fasterxml.jackson.datatype.guava.GuavaModule} and {@link com.fasterxml.jackson.datatype.jdk7.Jdk7Module}.
     */
    public static FeignClientFactory standard() {
        return standard(DEFAULT_TIMEOUT_OPTIONS);
    }

    /**
     * Provides a {@link FeignClientFactory} with an {@link ObjectMapper} configured with {@link
     * com.fasterxml.jackson.datatype.guava.GuavaModule} and {@link com.fasterxml.jackson.datatype.jdk7.Jdk7Module}.
     */
    public static FeignClientFactory standard(Request.Options timeoutOptions) {
        return withMapper(ObjectMappers.guavaJdk7(), timeoutOptions);
    }

    /**
     * Provides a {@link FeignClientFactory} compatible with jackson 2.4.
     */
    public static FeignClientFactory standardJackson24() {
        return standardJackson24(DEFAULT_TIMEOUT_OPTIONS);
    }

    /**
     * Provides a {@link FeignClientFactory} compatible with jackson 2.4.
     */
    public static FeignClientFactory standardJackson24(Request.Options timeoutOptions) {
        return withEncoderAndDecoder(
                new Jackson24Encoder(ObjectMappers.guavaJdk7()),
                new JacksonDecoder(ObjectMappers.guavaJdk7()),
                timeoutOptions);
    }

    /**
     * Provides a {@link FeignClientFactory} with an unmodified {@link ObjectMapper}.
     */
    public static FeignClientFactory vanilla() {
        return vanilla(DEFAULT_TIMEOUT_OPTIONS);
    }

    /**
     * Provides a {@link FeignClientFactory} with an unmodified {@link ObjectMapper}.
     */
    public static FeignClientFactory vanilla(Request.Options timeoutOptions) {
        return withMapper(ObjectMappers.vanilla(), timeoutOptions);
    }

    /**
     * Provides a {@link FeignClientFactory} with the specified {@link ObjectMapper}.
     */
    public static FeignClientFactory withMapper(ObjectMapper mapper) {
        return withMapper(mapper, DEFAULT_TIMEOUT_OPTIONS);
    }

    /**
     * Provides a {@link FeignClientFactory} with the specified {@link ObjectMapper}.
     */
    public static FeignClientFactory withMapper(ObjectMapper mapper, Request.Options timeoutOptions) {
        return withEncoderAndDecoder(new JacksonEncoder(mapper), new JacksonDecoder(mapper), timeoutOptions);
    }

    /**
     * Provides a {@link FeignClientFactory} with the specified {@link Encoder} and {@link Decoder}.
     */
    private static FeignClientFactory withEncoderAndDecoder(Encoder encoder, Decoder decoder,
            Request.Options timeoutOptions) {
        return FeignClientFactory.of(
                new SlashEncodingContract(new GuavaOptionalAwareContract(new JaxRsWithHeaderAndQueryMapContract())),
                new InputStreamDelegateEncoder(new TextDelegateEncoder(encoder)),
                new OptionalAwareDecoder(new InputStreamDelegateDecoder(new TextDelegateDecoder(decoder))),
                FeignSerializableErrorErrorDecoder.INSTANCE,
                FeignClientFactory.okHttpClient(),
                NeverRetryingBackoffStrategy.INSTANCE,
                timeoutOptions);
    }

}
