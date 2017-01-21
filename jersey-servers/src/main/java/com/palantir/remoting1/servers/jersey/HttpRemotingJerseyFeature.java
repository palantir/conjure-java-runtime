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

package com.palantir.remoting1.servers.jersey;

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

        // Optional handling
        // TODO(rfink) Should consider dropping this and requiring that the underlying Jersey server
        // (Witchcraft or Dropwizard 1.x) has support for optionals.
        context.register(GuavaOptionalMessageBodyWriter.class);
        context.register(GuavaOptionalParamConverterProvider.class);
        context.register(Java8OptionalMessageBodyWriter.class);
        context.register(Java8OptionalParamConverterProvider.class);

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
         * com.palantir.remoting1.errors.SerializableError serialized errors}. This is the recommended setting.
         */
        DO_NOT_PROPAGATE
    }
}
