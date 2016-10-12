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

package com.palantir.remoting1.tracing.okhttp3;


import com.palantir.remoting1.tracing.TraceState;
import com.palantir.remoting1.tracing.Traces;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** An OkHttp interceptor that adds Zipkin-style trace/span/parent-span headers to the HTTP request. */
public enum OkhttpTraceInterceptor implements Interceptor {
    INSTANCE;

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        TraceState callState = Traces.startSpan(request.method() + " " + request.url());
        Request.Builder tracedRequest = request.newBuilder()
                .addHeader(Traces.Headers.TRACE_ID, callState.getTraceId())
                .addHeader(Traces.Headers.SPAN_ID, callState.getSpanId());
        if (callState.getParentSpanId().isPresent()) {
            tracedRequest.header(Traces.Headers.PARENT_SPAN_ID, callState.getParentSpanId().get());
        }

        try {
            return chain.proceed(tracedRequest.build());
        } finally {
            Traces.completeSpan();
        }
    }
}
