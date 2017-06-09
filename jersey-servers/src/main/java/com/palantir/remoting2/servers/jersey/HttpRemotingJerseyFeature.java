/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.servers.jersey;

import com.fasterxml.jackson.jaxrs.cbor.JacksonCBORProvider;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;


public final class HttpRemotingJerseyFeature implements Feature {
    public static final HttpRemotingJerseyFeature DEFAULT = with(StacktracePropagation.DO_NOT_PROPAGATE);
    private final boolean propagateStackTraces;

    private HttpRemotingJerseyFeature(StacktracePropagation stacktracePropagation) {
        this.propagateStackTraces = stacktracePropagation == StacktracePropagation.PROPAGATE;
    }

    public static HttpRemotingJerseyFeature with(StacktracePropagation stacktracePropagation) {
        return new HttpRemotingJerseyFeature(stacktracePropagation);
    }

    /**
     * Server-side stacktraces are serialized and transferred to the client iff {@code serializeStacktrace} is {@code
     * true}. Configures a Jersey server w.r.t. http-remoting conventions: registers tracer filters and
     * exception mappers.
     */
    @Override
    public boolean configure(FeatureContext context) {
        // Exception mappers
        context.register(new IllegalArgumentExceptionMapper(propagateStackTraces));
        context.register(new NoContentExceptionMapper());
        context.register(new RuntimeExceptionMapper(propagateStackTraces));
        context.register(new WebApplicationExceptionMapper(propagateStackTraces));
        context.register(new RemoteExceptionMapper());

        // Cbor handling
        context.register(new JacksonCBORProvider(ObjectMappers.newCborServerObjectMapper()));

        // Optional handling
        context.register(GuavaOptionalMessageBodyWriter.class);
        context.register(GuavaOptionalParamConverterProvider.class);
        context.register(Java8OptionalMessageBodyWriter.class);
        context.register(Java8OptionalParamConverterProvider.class);
        context.register(Java8OptionalIntMessageBodyWriter.class);
        context.register(Java8OptionalIntParamConverterProvider.class);
        context.register(Java8OptionalDoubleMessageBodyWriter.class);
        context.register(Java8OptionalDoubleParamConverterProvider.class);
        context.register(Java8OptionalLongMessageBodyWriter.class);
        context.register(Java8OptionalLongParamConverterProvider.class);

        // DateTime
        context.register(InstantParamConverterProvider.class);
        context.register(ZonedDateTimeParamConverterProvider.class);

        // Tracing
        context.register(new TraceEnrichingFilter());

        return true;
    }

    public enum StacktracePropagation {
        /**
         * The inverse of {@link #DO_NOT_PROPAGATE}. Note that this may leak sensitive information from servers to
         * clients.
         */
        PROPAGATE,

        /**
         * Configures exception serializers to not include exception stacktraces in {@link
         * com.palantir.remoting2.errors.SerializableError serialized errors}. This is the recommended setting.
         */
        DO_NOT_PROPAGATE
    }
}
