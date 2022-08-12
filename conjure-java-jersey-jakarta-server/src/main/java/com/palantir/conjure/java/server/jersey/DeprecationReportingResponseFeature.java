/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import org.glassfish.jersey.server.model.AnnotatedMethod;

/**
 * Adds HTTP response headers to indicate endpoint deprecation.
 *
 * <p>https://tools.ietf.org/id/draft-dalal-deprecation-header-01.html#rfc.section.2.1
 */
enum DeprecationReportingResponseFeature implements DynamicFeature {
    INSTANCE;

    private static final String DEPRECATION = "deprecation";
    private static final String IS_DEPRECATED = "true";

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {
        final AnnotatedMethod annotatedMethod = new AnnotatedMethod(resourceInfo.getResourceMethod());
        if (annotatedMethod.getAnnotation(Deprecated.class) != null) {
            context.register(DeprecationReportingResponseFilter.INSTANCE, ContainerResponseFilter.class);
        }
    }

    private enum DeprecationReportingResponseFilter implements ContainerResponseFilter {
        INSTANCE;

        @Override
        public void filter(ContainerRequestContext _requestContext, ContainerResponseContext responseContext) {
            responseContext.getHeaders().add(DEPRECATION, IS_DEPRECATED);
        }
    }
}
