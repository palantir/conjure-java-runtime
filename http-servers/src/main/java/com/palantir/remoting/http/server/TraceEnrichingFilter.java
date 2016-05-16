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

package com.palantir.remoting.http.server;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.palantir.tracing.TraceState;
import com.palantir.tracing.Traces;
import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

@Provider
public final class TraceEnrichingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String operation = new StringBuilder(requestContext.getMethod())
                .append(" /")
                .append(requestContext.getUriInfo().getPath())
                .toString();

        String traceId = requestContext.getHeaderString(Traces.Headers.TRACE_ID);
        String parentSpanId = requestContext.getHeaderString(Traces.Headers.PARENT_SPAN_ID);
        String spanId = requestContext.getHeaderString(Traces.Headers.SPAN_ID);

        if (Strings.isNullOrEmpty(traceId)) {
            // no trace for this request, just derive a new one
            Traces.deriveTrace(operation);
        } else {
            // defend against badly formed requests
            if (spanId == null) {
                spanId = TraceState.randomId();
            }

            Traces.setTrace(TraceState.builder()
                    .traceId(traceId)
                    .parentSpanId(Optional.fromNullable(parentSpanId))
                    .spanId(spanId)
                    .operation(operation)
                    .build());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        Optional<TraceState> maybeTrace = Traces.complete();
        if (maybeTrace.isPresent()) {
            TraceState trace = maybeTrace.get();
            headers.putSingle(Traces.Headers.TRACE_ID, trace.getTraceId());
            headers.putSingle(Traces.Headers.SPAN_ID, trace.getSpanId());
            if (trace.getParentSpanId().isPresent()) {
                headers.putSingle(Traces.Headers.PARENT_SPAN_ID, trace.getParentSpanId().get());
            }
        }
    }

}
