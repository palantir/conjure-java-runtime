/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

enum Feign {
    ;

    static Builder builder() {
        return new Builder();
    }

    /**
     * <br> Configuration keys are formatted as unresolved <a href= "http://docs.oracle.com/javase/6/docs/jdk/api/javadoc/doclet/com/sun/javadoc/SeeTag.html"
     * >see tags</a>. <br> For example. <ul> <li>{@code Route53}: would match a class such as {@code
     * denominator.route53.Route53} <li>{@code Route53#list()}: would match a method such as {@code
     * denominator.route53.Route53#list()} <li>{@code Route53#listAt(Marker)}: would match a method
     * such as {@code denominator.route53.Route53#listAt(denominator.route53.Marker)} <li>{@code
     * Route53#listByNameAndType(String, String)}: would match a method such as {@code
     * denominator.route53.Route53#listAt(String, String)} </ul> <br> Note that there is no whitespace
     * expected in a key!
     *
     * @param type type of the Feign interface.
     * @param method invoked method, present on {@code type} or its super.
     */
    @SuppressWarnings("ModifiedControlVariable")
    static String configKey(Class<?> type, Method method) {
        StringBuilder builder = new StringBuilder();
        builder.append(type.getSimpleName());
        builder.append('#').append(method.getName()).append('(');
        for (Type param : method.getGenericParameterTypes()) {
            param = Types.resolve(type, type, param);
            builder.append(Types.getRawType(param).getSimpleName()).append(',');
        }
        if (method.getParameterTypes().length > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.append(')').toString();
    }

    @SuppressWarnings("HiddenField")
    static final class Builder {

        private Contract contract;
        private Client client;
        private Encoder encoder;
        private Decoder decoder;
        private ErrorDecoder errorDecoder;

        private Builder() {}

        Builder contract(Contract contract) {
            this.contract = contract;
            return this;
        }

        Builder client(Client client) {
            this.client = client;
            return this;
        }

        Builder encoder(Encoder encoder) {
            this.encoder = encoder;
            return this;
        }

        Builder decoder(Decoder decoder) {
            this.decoder = decoder;
            return this;
        }

        Builder errorDecoder(ErrorDecoder errorDecoder) {
            this.errorDecoder = errorDecoder;
            return this;
        }

        @SuppressWarnings("ProxyNonConstantType")
        <T> T build(Class<T> type) {
            List<MethodMetadata> metadata = contract.parseAndValidateMetadata(type);

            SynchronousMethodHandler.Factory factory = new SynchronousMethodHandler.Factory(client);

            Map<String, MethodHandler> nameToHandler = new LinkedHashMap<String, MethodHandler>();
            for (MethodMetadata md : metadata) {
                BuildTemplateByResolvingArgs buildTemplate;
                if (!md.formParams().isEmpty()) {
                    buildTemplate = new BuildFormEncodedTemplateFromArgs(md, encoder);
                } else if (md.bodyIndex() != null) {
                    buildTemplate = new BuildEncodedTemplateFromArgs(md, encoder);
                } else {
                    buildTemplate = new BuildTemplateByResolvingArgs(md);
                }
                nameToHandler.put(md.configKey(), factory.create(md, buildTemplate, decoder, errorDecoder));
            }

            Map<Method, MethodHandler> methodToHandler = new HashMap<>();
            for (Method method : type.getMethods()) {
                if (method.getDeclaringClass() == Object.class || method.isDefault()) {
                    continue;
                }
                methodToHandler.put(method, nameToHandler.get(Feign.configKey(type, method)));
            }

            InvocationHandler handler = new FeignInvocationHandler(methodToHandler);
            T proxy = (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);

            return proxy;
        }
    }

    private static class FeignInvocationHandler implements InvocationHandler {

        private final Map<Method, MethodHandler> dispatch;

        FeignInvocationHandler(Map<Method, MethodHandler> dispatch) {
            this.dispatch = Util.checkNotNull(dispatch, "dispatch");
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

            if ("equals".equals(method.getName())) {
                try {
                    Object otherHandler =
                            args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
                    return equals(otherHandler);
                } catch (IllegalArgumentException e) {
                    return false;
                }
            } else if ("hashCode".equals(method.getName())) {
                return hashCode();
            } else if ("toString".equals(method.getName())) {
                return toString();
            }
            return dispatch.get(method).invoke(args);
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
