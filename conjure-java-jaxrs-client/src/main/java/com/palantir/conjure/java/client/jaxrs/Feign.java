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

import com.palantir.conjure.java.client.jaxrs.ReflectiveFeign.ParseHandlersByName;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Feign's purpose is to ease development against http apis that feign restfulness. <br> In
 * implementation, Feign is a {@link Feign#newInstance factory} for generating {@link Target
 * targeted} http apis.
 */
abstract class Feign {

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
     * @param targetType {@link Target#type() type} of the Feign interface.
     * @param method invoked method, present on {@code type} or its super.
     */
    static String configKey(Class<?> targetType, Method method) {
        StringBuilder builder = new StringBuilder();
        builder.append(targetType.getSimpleName());
        builder.append('#').append(method.getName()).append('(');
        for (Type param : method.getGenericParameterTypes()) {
            param = Types.resolve(targetType, targetType, param);
            builder.append(Types.getRawType(param).getSimpleName()).append(',');
        }
        if (method.getParameterTypes().length > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.append(')').toString();
    }

    /**
     * Returns a new instance of an HTTP API, defined by annotations in the {@link Feign Contract},
     * for the specified {@code target}. You should cache this result.
     */
    abstract <T> T newInstance(Target<T> target);

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

        <T> T target(Target<T> target) {
            SynchronousMethodHandler.Factory synchronousMethodHandlerFactory =
                    new SynchronousMethodHandler.Factory(client);
            ParseHandlersByName handlersByName =
                    new ParseHandlersByName(contract, encoder, decoder, errorDecoder, synchronousMethodHandlerFactory);
            return new ReflectiveFeign(handlersByName).newInstance(target);
        }
    }
}
