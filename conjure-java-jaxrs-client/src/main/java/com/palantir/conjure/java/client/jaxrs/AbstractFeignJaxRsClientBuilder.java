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
import com.palantir.conjure.java.annotations.JaxRsClient;
import com.palantir.conjure.java.annotations.JaxRsServer;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.conjure.java.okhttp.HostEventsSink;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.dialogue.hc5.ApacheHttpClientChannels;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;

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

    abstract ObjectMapper getObjectMapper();

    abstract ObjectMapper getCborObjectMapper();

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
        @SuppressWarnings("for-rollout:deprecation")
        Channel channel = DialogueChannel.builder()
                .channelName(name)
                .channelFactory(uri -> ApacheHttpClientChannels.createSingleUri(uri, client))
                .clientConfiguration(hydratedConfiguration)
                .buildNonLiveReloading();

        return create(name, serviceClass, channel, RUNTIME, getObjectMapper(), getCborObjectMapper());
    }

    static <T> T create(
            @Safe String clientNameForLogging,
            Class<T> serviceClass,
            Channel channel,
            ConjureRuntime runtime,
            ObjectMapper jsonMapper,
            ObjectMapper cborMapper) {
        verifyClientUsageAnnotations(serviceClass);
        return Feign.builder()
                .contract(createContract())
                .encoder(createEncoder(clientNameForLogging, jsonMapper, cborMapper))
                .decoder(createDecoder(clientNameForLogging, jsonMapper, cborMapper))
                .errorDecoder(new DialogueFeignClient.RemoteExceptionDecoder(runtime))
                .client(new DialogueFeignClient(serviceClass, channel, runtime))
                .build(serviceClass);
    }

    private static Contract createContract() {
        Contract contract = new JaxRsContract();
        contract = new GuavaOptionalAwareContract(contract);
        contract = new Java8OptionalAwareContract(contract);
        contract = new MethodHeaderEnrichmentContract(contract);
        contract = new EndpointNameHeaderEnrichmentContract(contract);
        return contract;
    }

    private static Decoder createDecoder(
            @Safe String clientNameForLogging, ObjectMapper jsonMapper, ObjectMapper cborMapper) {
        Decoder decoder = new JacksonDecoder(jsonMapper);
        decoder = new CborDelegateDecoder(cborMapper, decoder);
        decoder = new TextDelegateDecoder(decoder);
        decoder = new InputStreamDelegateDecoder(clientNameForLogging, decoder);
        decoder = new EmptyContainerDecoder(jsonMapper, decoder);
        decoder = new GuavaOptionalAwareDecoder(decoder);
        decoder = new Java8OptionalAwareDecoder(decoder);
        decoder = new NeverReturnNullDecoder(decoder);
        return decoder;
    }

    private static Encoder createEncoder(
            @Safe String clientNameForLogging, ObjectMapper jsonMapper, ObjectMapper cborMapper) {
        Encoder encoder = new JacksonEncoder(jsonMapper);
        encoder = new CborDelegateEncoder(cborMapper, encoder);
        encoder = new TextDelegateEncoder(encoder);
        encoder = new InputStreamDelegateEncoder(clientNameForLogging, encoder);
        return encoder;
    }

    private static void verifyClientUsageAnnotations(Class<?> serviceClass) {
        if (serviceClass.getAnnotation(JaxRsClient.class) == null
                && serviceClass.getAnnotation(JaxRsServer.class) != null) {
            throw new SafeIllegalArgumentException(
                    "Service class should not be used as a client because it is annotated with \"@JaxRsServer\" and "
                            + "should only used as a server resource",
                    SafeArg.of("serviceClass", serviceClass));
        }
    }
}
