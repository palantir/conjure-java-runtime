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

package com.palantir.remoting3.servers.jersey;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.palantir.remoting.api.tracing.Span;
import com.palantir.remoting.api.tracing.SpanType;
import com.palantir.remoting.api.tracing.TraceHttpHeaders;
import com.palantir.remoting3.tracing.Tracer;
import com.palantir.remoting3.tracing.Tracers;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.uri.UriTemplate;

@Provider
public final class TraceEnrichingFilter implements ContainerRequestFilter, ContainerResponseFilter {
    public static final TraceEnrichingFilter INSTANCE = new TraceEnrichingFilter();

    @Context
    private ExtendedUriInfo uriInfo;

    // Handles incoming request
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = Optional.ofNullable(uriInfo)
                .map(ExtendedUriInfo::getMatchedModelResource)
                .map(Resource::getPath)
                .orElse("(unknown)");

        String operation = requestContext.getMethod() + " "
                + getParameterizedPath((ExtendedUriInfo) requestContext.getUriInfo());
        // The following strings are all nullable
        String traceId = requestContext.getHeaderString(TraceHttpHeaders.TRACE_ID);
        String spanId = requestContext.getHeaderString(TraceHttpHeaders.SPAN_ID);

        // Set up thread-local span that inherits state from HTTP headers
        if (Strings.isNullOrEmpty(traceId)) {
            // HTTP request did not indicate a trace; initialize trace state and create a span.
            Tracer.initTrace(Optional.empty(), Tracers.randomId());
            Tracer.startSpan(operation, SpanType.SERVER_INCOMING);
        } else {
            Tracer.initTrace(hasSampledHeader(requestContext), traceId);
            if (spanId == null) {
                Tracer.startSpan(operation, SpanType.SERVER_INCOMING);
            } else {
                // caller's span is this span's parent.
                Tracer.startSpan(operation, spanId, SpanType.SERVER_INCOMING);
            }
        }

        // Give asynchronous downstream handlers access to the trace id
        requestContext.setProperty("com.palantir.remoting3.traceId", Tracer.getTraceId());
    }

    // Handles outgoing response
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        Optional<Span> maybeSpan = Tracer.completeSpan();
        if (maybeSpan.isPresent()) {
            Span span = maybeSpan.get();
            headers.putSingle(TraceHttpHeaders.TRACE_ID, span.getTraceId());
        }
    }

    // Returns true iff the context contains a "1" X-B3-Sampled header, or absent if there is no such header.
    private static Optional<Boolean> hasSampledHeader(ContainerRequestContext context) {
        String header = context.getHeaderString(TraceHttpHeaders.IS_SAMPLED);
        if (header == null) {
            return Optional.empty();
        } else {
            return Optional.of(header.equals("1"));
        }
    }

    public String getParameterizedPath(ExtendedUriInfo extendedUriInfo) {
        List<UriTemplate> matchedTemplates = extendedUriInfo.getMatchedTemplates();
        StringBuilder parameterizedPath = new StringBuilder();
        for (UriTemplate matchedTemplate : Lists.reverse(matchedTemplates)) {
            String stringTemplate = normalizeTemplate(matchedTemplate.getTemplate());
            if (!stringTemplate.equals("/")) {
                parameterizedPath.append(stringTemplate);
            }
        }
        if (parameterizedPath.length() == 0) {
            return "/";
        }
        return parameterizedPath.toString();
    }

    private static String normalizeTemplate(String rawTemplate) {
        return addLeadingSlash(removeTrailingSlash(rawTemplate));
    }

    private static String addLeadingSlash(String rawTemplate) {
        return rawTemplate.startsWith("/") ? rawTemplate : "/" + rawTemplate;
    }

    private static String removeTrailingSlash(String rawTemplate) {
        return rawTemplate.endsWith("/") ? rawTemplate.substring(0, rawTemplate.length() - 1) : rawTemplate;
    }

}
