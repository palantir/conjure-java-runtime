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
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.tracing.jersey.TraceEnrichingFilter;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

public enum ConjureJerseyFeature implements Feature {
    INSTANCE;

    private JerseyServerMetrics metrics;

    ConjureJerseyFeature() {
        this.metrics = JerseyServerMetrics.of(SharedTaggedMetricRegistries.getSingleton());
    }

    public JerseyServerMetrics getMetrics() {
        return metrics;
    }

    /**
     * Configures a Jersey server w.r.t. conjure-java-runtime conventions: registers tracer filters and exception
     * mappers.
     */
    @Override
    public boolean configure(FeatureContext context) {
        // Exception mappers
        context.register(new IllegalArgumentExceptionMapper(metrics));
        context.register(new NoContentExceptionMapper());
        context.register(new RetryableExceptionMapper(metrics));
        context.register(new RuntimeExceptionMapper(metrics));
        context.register(new WebApplicationExceptionMapper());
        context.register(new RemoteExceptionMapper());
        context.register(new ServiceExceptionMapper(metrics));
        context.register(new QosExceptionMapper());
        context.register(new ThrowableExceptionMapper(metrics));

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

        return true;
    }
}
