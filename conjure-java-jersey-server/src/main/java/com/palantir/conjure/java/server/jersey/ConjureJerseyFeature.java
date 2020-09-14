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

import com.fasterxml.jackson.jaxrs.cbor.JacksonCBORProvider;
import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.logsafe.Preconditions;
import com.palantir.tracing.jersey.TraceEnrichingFilter;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

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
        context.register(exceptionListener.augment(new IllegalArgumentExceptionMapper()));
        context.register(exceptionListener.augment(new NoContentExceptionMapper()));
        context.register(exceptionListener.augment(new RetryableExceptionMapper()));
        context.register(exceptionListener.augment(new RuntimeExceptionMapper()));
        context.register(exceptionListener.augment(new WebApplicationExceptionMapper()));
        context.register(exceptionListener.augment(new RemoteExceptionMapper()));
        context.register(exceptionListener.augment(new ServiceExceptionMapper()));
        context.register(exceptionListener.augment(new QosExceptionMapper()));
        context.register(exceptionListener.augment(new ThrowableExceptionMapper()));
        JacksonExceptionMappers.configure(context, exceptionListener);

        // Cbor handling
        context.register(new JacksonCBORProvider(ObjectMappers.newCborServerObjectMapper()));

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
        Builder exceptionListener(ExceptionListener value) {
            this.exceptionListener = value;
            return this;
        }

        Feature build() {
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

        default <T extends Throwable> ExceptionMapper<T> augment(ExceptionMapper<T> delegate) {
            return new ExceptionMapper<T>() {
                @Override
                public Response toResponse(T exception) {
                    try {
                        onException(exception);
                        return delegate.toResponse(exception);
                    } finally {
                        afterResponseBuilt();
                    }
                }

                @Override
                public String toString() {
                    return "ExceptionListener{delegate=" + delegate + '}';
                }
            };
        }
    }

    enum NoOpListener implements ExceptionListener {
        INSTANCE;

        @Override
        public void onException(Throwable _throwable) {}

        @Override
        public void afterResponseBuilt() {}

        @Override
        public <T extends Throwable> ExceptionMapper<T> augment(ExceptionMapper<T> delegate) {
            return delegate;
        }
    }
}
