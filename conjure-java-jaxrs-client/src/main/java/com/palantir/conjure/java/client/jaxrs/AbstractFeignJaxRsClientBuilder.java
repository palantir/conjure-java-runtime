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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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

    @SuppressWarnings("ProxyNonConstantType")
    static <T> T create(
            @Safe String clientNameForLogging,
            Class<T> serviceClass,
            Channel channel,
            ConjureRuntime runtime,
            ObjectMapper jsonMapper,
            ObjectMapper cborMapper) {
        verifyClientUsageAnnotations(serviceClass);

        Contract contract = createContract();
        Encoder encoder = createEncoder(clientNameForLogging, jsonMapper, cborMapper);
        Decoder decoder = createDecoder(clientNameForLogging, jsonMapper, cborMapper);
        ErrorDecoder errorDecoder = new DialogueFeignClient.RemoteExceptionDecoder(runtime);
        Client client = new DialogueFeignClient(serviceClass, channel, runtime, FeignDialogueTarget.BASE_URL);
        Target<T> target = new FeignDialogueTarget<>(serviceClass, channel);

        SynchronousMethodHandler.Factory methodHandlerFactory = new SynchronousMethodHandler.Factory(client);

        List<MethodMetadata> metadata = contract.parseAndValidateMetadata(target.type());

        Map<Method, MethodHandler> methodHandlers = new LinkedHashMap<Method, MethodHandler>();
        for (MethodMetadata md : metadata) {
            BuildTemplateByResolvingArgs buildTemplate;
            if (!md.formParams().isEmpty()) {
                buildTemplate = new BuildFormEncodedTemplateFromArgs(md, encoder);
            } else if (md.bodyIndex() != null) {
                buildTemplate = new BuildEncodedTemplateFromArgs(md, encoder);
            } else {
                buildTemplate = new BuildTemplateByResolvingArgs(md);
            }
            methodHandlers.put(
                    md.method(), methodHandlerFactory.create(target, md, buildTemplate, decoder, errorDecoder));
        }

        InvocationHandler handler = new FeignInvocationHandler(target, methodHandlers);
        T proxy = (T) Proxy.newProxyInstance(target.type().getClassLoader(), new Class<?>[] {target.type()}, handler);

        return proxy;
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

    /**
     * Exists to fix equality computation between Feign client instances, which only compare the serviceClass and
     * target. However, there's a great deal of other configuration, and we handle failover/retries in Dialogue
     * which makes every client appear to use the same URL.
     */
    private record FeignDialogueTarget<T>(Class<T> serviceClass, Channel channel) implements Target<T> {
        private static final String BASE_URL = "dialogue://feign";

        @Override
        public Class<T> type() {
            return serviceClass;
        }

        @Override
        public String url() {
            return BASE_URL;
        }

        @Override
        public Request apply(RequestTemplate input) {
            if (input.url().indexOf("http") != 0) {
                input.insert(0, url());
            }
            return input.request();
        }
    }

    private static class BuildTemplateByResolvingArgs implements RequestTemplate.Factory {

        @SuppressWarnings("VisibilityModifier")
        final MethodMetadata metadata;

        private final Map<Integer, Expander> indexToExpander = new LinkedHashMap<Integer, Expander>();

        private BuildTemplateByResolvingArgs(MethodMetadata metadata) {
            this.metadata = metadata;
            if (metadata.indexToExpander() != null) {
                indexToExpander.putAll(metadata.indexToExpander());
                return;
            }
            if (metadata.indexToExpanderClass().isEmpty()) {
                return;
            }
            for (Entry<Integer, Class<? extends Expander>> indexToExpanderClass :
                    metadata.indexToExpanderClass().entrySet()) {
                try {
                    indexToExpander.put(
                            indexToExpanderClass.getKey(),
                            indexToExpanderClass
                                    .getValue()
                                    .getDeclaredConstructor()
                                    .newInstance());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public RequestTemplate create(Object[] argv) {
            RequestTemplate mutable = new RequestTemplate(metadata.template());
            if (metadata.urlIndex() != null) {
                int urlIndex = metadata.urlIndex();
                Util.checkArgument(argv[urlIndex] != null, "URI parameter %s was null", urlIndex);
                mutable.insert(0, String.valueOf(argv[urlIndex]));
            }
            Map<String, Object> varBuilder = new LinkedHashMap<String, Object>();
            for (Entry<Integer, Collection<String>> entry :
                    metadata.indexToName().entrySet()) {
                int index = entry.getKey();
                Object value = argv[entry.getKey()];
                if (value != null) { // Null values are skipped.
                    if (indexToExpander.containsKey(index)) {
                        value = expandElements(indexToExpander.get(index), value);
                    }
                    for (String name : entry.getValue()) {
                        varBuilder.put(name, value);
                    }
                }
            }

            RequestTemplate template = resolve(argv, mutable, varBuilder);
            if (metadata.queryMapIndex() != null) {
                // add query map parameters after initial resolve so that they take
                // precedence over any predefined values
                template = addQueryMapQueryParameters(argv, template);
            }

            if (metadata.headerMapIndex() != null) {
                template = addHeaderMapHeaders(argv, template);
            }

            return template;
        }

        private Object expandElements(Expander expander, Object value) {
            if (value instanceof Iterable<?> iterable) {
                return expandIterable(expander, iterable);
            }
            return expander.expand(value);
        }

        private List<String> expandIterable(Expander expander, Iterable<?> value) {
            List<String> values = new ArrayList<String>();
            for (Object element : value) {
                if (element != null) {
                    values.add(expander.expand(element));
                }
            }
            return values;
        }

        private RequestTemplate addHeaderMapHeaders(Object[] argv, RequestTemplate mutable) {
            Map<Object, Object> headerMap = (Map<Object, Object>) argv[metadata.headerMapIndex()];
            for (Entry<Object, Object> currEntry : headerMap.entrySet()) {
                Util.checkState(
                        currEntry.getKey().getClass() == String.class,
                        "HeaderMap key must be a String: %s",
                        currEntry.getKey());

                Collection<String> values = new ArrayList<String>();

                Object currValue = currEntry.getValue();
                if (currValue instanceof Iterable<?> iterable) {
                    Iterator<?> iter = iterable.iterator();
                    while (iter.hasNext()) {
                        Object nextObject = iter.next();
                        values.add(nextObject == null ? null : nextObject.toString());
                    }
                } else {
                    values.add(currValue == null ? null : currValue.toString());
                }

                mutable.header((String) currEntry.getKey(), values);
            }
            return mutable;
        }

        private RequestTemplate addQueryMapQueryParameters(Object[] argv, RequestTemplate mutable) {
            Map<Object, Object> queryMap = (Map<Object, Object>) argv[metadata.queryMapIndex()];
            for (Entry<Object, Object> currEntry : queryMap.entrySet()) {
                Util.checkState(
                        currEntry.getKey().getClass() == String.class,
                        "QueryMap key must be a String: %s",
                        currEntry.getKey());

                Collection<String> values = new ArrayList<String>();

                Object currValue = currEntry.getValue();
                if (currValue instanceof Iterable<?> iterable) {
                    Iterator<?> iter = iterable.iterator();
                    while (iter.hasNext()) {
                        Object nextObject = iter.next();
                        values.add(nextObject == null ? null : nextObject.toString());
                    }
                } else {
                    values.add(currValue == null ? null : currValue.toString());
                }

                mutable.query(metadata.queryMapEncoded(), (String) currEntry.getKey(), values);
            }
            return mutable;
        }

        RequestTemplate resolve(Object[] _argv, RequestTemplate mutable, Map<String, Object> variables) {
            return mutable.resolve(variables);
        }
    }

    private static final class BuildFormEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

        private final Encoder encoder;

        private BuildFormEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder) {
            super(metadata);
            this.encoder = encoder;
        }

        @Override
        RequestTemplate resolve(Object[] argv, RequestTemplate mutable, Map<String, Object> variables) {
            Map<String, Object> formVariables = new LinkedHashMap<String, Object>();
            for (Entry<String, Object> entry : variables.entrySet()) {
                if (metadata.formParams().contains(entry.getKey())) {
                    formVariables.put(entry.getKey(), entry.getValue());
                }
            }
            encoder.encode(formVariables, Encoder.MAP_STRING_WILDCARD, mutable);
            return super.resolve(argv, mutable, variables);
        }
    }

    private static final class BuildEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

        private final Encoder encoder;

        private BuildEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder) {
            super(metadata);
            this.encoder = encoder;
        }

        @Override
        RequestTemplate resolve(Object[] argv, RequestTemplate mutable, Map<String, Object> variables) {
            Object body = argv[metadata.bodyIndex()];
            Util.checkArgument(body != null, "Body parameter %s was null", metadata.bodyIndex());
            encoder.encode(body, metadata.bodyType(), mutable);
            return super.resolve(argv, mutable, variables);
        }
    }
}
