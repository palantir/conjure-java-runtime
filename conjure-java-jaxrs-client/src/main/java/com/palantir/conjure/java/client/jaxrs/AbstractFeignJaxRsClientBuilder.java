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
import com.palantir.conjure.java.client.jaxrs.feignimpl.CborDelegateDecoder;
import com.palantir.conjure.java.client.jaxrs.feignimpl.CborDelegateEncoder;
import com.palantir.conjure.java.client.jaxrs.feignimpl.EmptyContainerDecoder;
import com.palantir.conjure.java.client.jaxrs.feignimpl.EndpointNameHeaderEnrichmentContract;
import com.palantir.conjure.java.client.jaxrs.feignimpl.GuavaOptionalAwareContract;
import com.palantir.conjure.java.client.jaxrs.feignimpl.GuavaOptionalAwareDecoder;
import com.palantir.conjure.java.client.jaxrs.feignimpl.InputStreamDelegateDecoder;
import com.palantir.conjure.java.client.jaxrs.feignimpl.InputStreamDelegateEncoder;
import com.palantir.conjure.java.client.jaxrs.feignimpl.Java8OptionalAwareContract;
import com.palantir.conjure.java.client.jaxrs.feignimpl.Java8OptionalAwareDecoder;
import com.palantir.conjure.java.client.jaxrs.feignimpl.NeverReturnNullDecoder;
import com.palantir.conjure.java.client.jaxrs.feignimpl.PathTemplateHeaderEnrichmentContract;
import com.palantir.conjure.java.client.jaxrs.feignimpl.SlashEncodingContract;
import com.palantir.conjure.java.client.jaxrs.feignimpl.TextDelegateDecoder;
import com.palantir.conjure.java.client.jaxrs.feignimpl.TextDelegateEncoder;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.conjure.java.okhttp.HostEventsSink;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.dialogue.hc4.ApacheHttpClientChannels;
import com.palantir.logsafe.Preconditions;
import feign.Contract;
import feign.Feign;
import feign.Logger;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson.JacksonDecoder;
import feign.jaxrs.JAXRSContract;

/** Not meant to be implemented outside of this library. */
abstract class AbstractFeignJaxRsClientBuilder {

    private static final ConjureRuntime RUNTIME =
            DefaultConjureRuntime.builder().build();
    private final ClientConfiguration config;

    private HostEventsSink hostEventsSink;

    AbstractFeignJaxRsClientBuilder(ClientConfiguration config) {
        Preconditions.checkArgument(!config.uris().isEmpty(), "Must provide at least one service URI");
        this.config = config;
    }

    protected abstract ObjectMapper getObjectMapper();

    protected abstract ObjectMapper getCborObjectMapper();

    /** Set the host metrics registry to use when constructing the OkHttp client. */
    final AbstractFeignJaxRsClientBuilder hostEventsSink(HostEventsSink newHostEventsSink) {
        Preconditions.checkNotNull(newHostEventsSink, "hostEventsSink can't be null");
        hostEventsSink = newHostEventsSink;
        return this;
    }

    final <T> T build(Class<T> serviceClass, UserAgent userAgent) {
        ClientConfiguration hydratedConfiguration = ClientConfiguration.builder()
                .from(config)
                .userAgent(Preconditions.checkNotNull(userAgent, "userAgent must be set"))
                .hostEventsSink(Preconditions.checkNotNull(hostEventsSink, "hostEventsSink must be set"))
                .build();
        String name = "JaxRsClient-" + serviceClass.getSimpleName();
        ApacheHttpClientChannels.CloseableClient client =
                ApacheHttpClientChannels.createCloseableHttpClient(hydratedConfiguration, name);
        Channel channel = DialogueChannel.builder()
                .channelName(name)
                .channelFactory(uri -> ApacheHttpClientChannels.createSingleUri(uri, client))
                .clientConfiguration(hydratedConfiguration)
                .buildNonLiveReloading();

        return create(serviceClass, channel, RUNTIME, getObjectMapper(), getCborObjectMapper());
    }

    static <T> T create(
            Class<T> serviceClass,
            Channel channel,
            ConjureRuntime runtime,
            ObjectMapper jsonObjectMapper,
            ObjectMapper cborObjectMapper) {
        // not used, simply for replacement
        String baseUrl = "dialogue://feign";
        return Feign.builder()
                .contract(createContract())
                .encoder(createEncoder(jsonObjectMapper, cborObjectMapper))
                .decoder(createDecoder(jsonObjectMapper, cborObjectMapper))
                .errorDecoder(new DialogueFeignClient.RemoteExceptionDecoder(runtime))
                .client(new DialogueFeignClient(serviceClass, channel, runtime, baseUrl))
                .logLevel(Logger.Level.NONE) // we use Dialogue for logging. (note that NONE is the default)
                .retryer(new Retryer.Default(0, 0, 1)) // use dialogue retry mechanism only
                .target(serviceClass, baseUrl);
    }

    private static Contract createContract() {
        return new EndpointNameHeaderEnrichmentContract(
                new PathTemplateHeaderEnrichmentContract(new SlashEncodingContract(
                        new Java8OptionalAwareContract(new GuavaOptionalAwareContract(new JAXRSContract())))));
    }

    private static Decoder createDecoder(ObjectMapper objectMapper, ObjectMapper cborObjectMapper) {
        return new NeverReturnNullDecoder(
                new Java8OptionalAwareDecoder(new GuavaOptionalAwareDecoder(new EmptyContainerDecoder(
                        objectMapper,
                        new InputStreamDelegateDecoder(new TextDelegateDecoder(
                                new CborDelegateDecoder(cborObjectMapper, new JacksonDecoder(objectMapper))))))));
    }

    private static Encoder createEncoder(ObjectMapper objectMapper, ObjectMapper cborObjectMapper) {
        return new InputStreamDelegateEncoder(new TextDelegateEncoder(
                new CborDelegateEncoder(cborObjectMapper, new ConjureFeignJacksonEncoder(objectMapper))));
    }
}
