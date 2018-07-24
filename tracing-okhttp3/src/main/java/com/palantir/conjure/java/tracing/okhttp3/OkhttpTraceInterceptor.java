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

package com.palantir.conjure.java.tracing.okhttp3;

import com.palantir.conjure.java.api.tracing.OpenSpan;
import com.palantir.conjure.java.api.tracing.SpanType;
import com.palantir.conjure.java.api.tracing.TraceHttpHeaders;
import com.palantir.conjure.java.tracing.Tracer;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** An OkHttp interceptor that adds Zipkin-style trace/span/parent-span headers to the HTTP request. */
public enum OkhttpTraceInterceptor implements Interceptor {
    INSTANCE;

    /** The HTTP header used to communicate API endpoint names internally. Not considered public API. */
    public static final String PATH_TEMPLATE_HEADER = "hr-path-template";

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        String spanName = request.method();
        String httpRemotingPath = request.header(PATH_TEMPLATE_HEADER);
        if (httpRemotingPath != null) {
            spanName = httpRemotingPath;
            request = request.newBuilder().removeHeader(PATH_TEMPLATE_HEADER).build();
        }

        OpenSpan span = Tracer.startSpan(spanName, SpanType.CLIENT_OUTGOING);
        Request.Builder tracedRequest = request.newBuilder()
                .addHeader(TraceHttpHeaders.TRACE_ID, Tracer.getTraceId())
                .addHeader(TraceHttpHeaders.SPAN_ID, span.getSpanId())
                .addHeader(TraceHttpHeaders.IS_SAMPLED, Tracer.isTraceObservable() ? "1" : "0");
        if (span.getParentSpanId().isPresent()) {
            tracedRequest.header(TraceHttpHeaders.PARENT_SPAN_ID, span.getParentSpanId().get());
        }

        Response response;
        try {
            response = chain.proceed(tracedRequest.build());
        } finally {
            Tracer.completeSpan();
        }

        return response;
    }

}
