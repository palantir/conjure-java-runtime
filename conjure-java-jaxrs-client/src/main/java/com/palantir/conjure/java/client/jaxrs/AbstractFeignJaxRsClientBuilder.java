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
import com.palantir.dialogue.hc5.ApacheHttpClientChannels;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import feign.Contract;
import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.RequestTemplate;
import feign.Retryer;
import feign.Target;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson.JacksonDecoder;
import java.util.Objects;

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
                .client(new DialogueFeignClient(serviceClass, channel, runtime, FeignDialogueTarget.BASE_URL))
                .logLevel(Logger.Level.NONE) // we use Dialogue for logging. (note that NONE is the default)
                .retryer(new Retryer.Default(0, 0, 1)) // use dialogue retry mechanism only
                .target(new FeignDialogueTarget<>(serviceClass, channel));
    }

    /**
     * Exists to fix equality computation between Feign client instances, which only compare the serviceClass and
     * target. However, there's a great deal of other configuration, and we handle failover/retries in Dialogue
     * which makes every client appear to use the same URL.
     */
    private static final class FeignDialogueTarget<T> implements Target<T> {
        private static final String BASE_URL = "dialogue://feign";

        private final Class<T> serviceClass;
        private final Target<T> delegate;
        // For equality checks
        private final Channel channel;

        FeignDialogueTarget(Class<T> serviceClass, Channel channel) {
            this.serviceClass = serviceClass;
            this.channel = channel;
            this.delegate = new HardCodedTarget<>(serviceClass, BASE_URL);
        }

        @Override
        public Class<T> type() {
            return serviceClass;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public String url() {
            return delegate.url();
        }

        @Override
        public Request apply(RequestTemplate input) {
            return delegate.apply(input);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            FeignDialogueTarget<?> that = (FeignDialogueTarget<?>) other;
            return serviceClass.equals(that.serviceClass)
                    && delegate.equals(that.delegate)
                    && channel.equals(that.channel);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceClass, delegate, channel);
        }
    }

    private static Contract createContract() {
        return new EndpointNameHeaderEnrichmentContract(
                new PathTemplateHeaderEnrichmentContract(new SlashEncodingContract(new Java8OptionalAwareContract(
                        new GuavaOptionalAwareContract(new CompatibleJaxRsContract())))));
    }

    private static Decoder createDecoder(
            @Safe String clientNameForLogging, ObjectMapper jsonMapper, ObjectMapper cborMapper) {
        return new NeverReturnNullDecoder(
                new Java8OptionalAwareDecoder(new GuavaOptionalAwareDecoder(new EmptyContainerDecoder(
                        jsonMapper,
                        new InputStreamDelegateDecoder(
                                clientNameForLogging,
                                new TextDelegateDecoder(
                                        new CborDelegateDecoder(cborMapper, new JacksonDecoder(jsonMapper))))))));
    }

    private static Encoder createEncoder(
            @Safe String clientNameForLogging, ObjectMapper jsonMapper, ObjectMapper cborMapper) {
        return new InputStreamDelegateEncoder(
                clientNameForLogging,
                new TextDelegateEncoder(
                        new CborDelegateEncoder(cborMapper, new ConjureFeignJacksonEncoder(jsonMapper))));
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
