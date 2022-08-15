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

package com.palantir.conjure.java.server.jersey;

import com.fasterxml.jackson.jakarta.rs.cbor.JacksonCBORProvider;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.logsafe.Preconditions;
import com.palantir.tracing.jersey.TraceEnrichingFilter;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

public enum ConjureJerseyFeature implements Feature {

    /**
     * Prefer {@link #builder()} which allows passing in a real {@link ExceptionListener} which records internal errors.
     */
    INSTANCE;

    /**
     * Configures a Jersey server w.r.t. conjure-java-runtime conventions: registers tracer filters and exception
     * mappers.
     */
    @Override
    public boolean configure(FeatureContext context) {
        return configure(context, NoOpListener.INSTANCE);
    }

    private static boolean configure(FeatureContext context, ExceptionListener exceptionListener) {
        // Exception mappers
        context.register(new NoContentExceptionMapper());
        context.register(new IllegalArgumentExceptionMapper(exceptionListener));
        context.register(new RetryableExceptionMapper(exceptionListener));
        context.register(new RuntimeExceptionMapper(exceptionListener));
        context.register(new WebApplicationExceptionMapper(exceptionListener));
        context.register(new RemoteExceptionMapper(exceptionListener));
        context.register(new ServiceExceptionMapper(exceptionListener));
        context.register(new QosExceptionMapper(exceptionListener));
        context.register(new ThrowableExceptionMapper(exceptionListener));
        JacksonExceptionMappers.configure(context, exceptionListener);

        // Cbor handling
        context.register(new JacksonCBORProvider(ObjectMappers.newServerCborMapper()));

        // Auth handling
        context.register(AuthHeaderParamConverterProvider.class);
        context.register(BearerTokenParamConverterProvider.class);

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

        // DateTime handling
        context.register(InstantParamConverterProvider.class);
        context.register(ZonedDateTimeParamConverterProvider.class);
        context.register(OffsetDateTimeParamConverterProvider.class);

        // Tracing
        context.register(new TraceEnrichingFilter());

        // Deprecation
        context.register(DeprecationReportingResponseFeature.INSTANCE);

        return true;
    }

    @CheckReturnValue
    public static Builder builder() {
        return new Builder();
    }

    @CheckReturnValue
    public static final class Builder {
        private ExceptionListener exceptionListener = NoOpListener.INSTANCE;

        private Builder() {}

        /**
         * Every throwable handled by the {@code ConjureJerseyFeature} is first passed to this {@code
         * exceptionListener}. This is a good opportunity to record metrics about the different types of exceptions.
         */
        public Builder exceptionListener(ExceptionListener value) {
            this.exceptionListener = value;
            return this;
        }

        public Feature build() {
            ExceptionListener listener = Preconditions.checkNotNull(exceptionListener, "exceptionListener");
            return new Feature() {
                @Override
                public boolean configure(FeatureContext context) {
                    return ConjureJerseyFeature.configure(context, listener);
                }

                @Override
                public String toString() {
                    return "ConjureJerseyFeature{}";
                }
            };
        }
    }

    /**
     * Implementors of this interface can record metrics based on the exceptions being thrown, and set/unset MDCs in
     * order to affect any log lines.
     */
    public interface ExceptionListener {

        void onException(Throwable throwable);

        /** Invoked in a finally block, so use this to unset any MDC values. */
        void afterResponseBuilt();
    }

    enum NoOpListener implements ExceptionListener {
        INSTANCE;

        @Override
        public void onException(Throwable _throwable) {}

        @Override
        public void afterResponseBuilt() {}
    }
}
