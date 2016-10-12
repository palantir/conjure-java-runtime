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

package com.palantir.remoting1.retrofit;

import com.palantir.remoting1.tracing.TraceState;
import com.palantir.remoting1.tracing.Traces;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;

public enum TraceInterceptor implements Interceptor {

    INSTANCE;

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        // instrument request
        TraceState callState = Traces.startSpan(request.method() + " " + request.urlString());
        Request.Builder instrumentedRequest = new Request.Builder()
                .headers(request.headers())
                .url(request.url())
                .method(request.method(), request.body())
                .header(Traces.Headers.TRACE_ID, callState.getTraceId())
                .header(Traces.Headers.SPAN_ID, callState.getSpanId());
        if (callState.getParentSpanId().isPresent()) {
            instrumentedRequest.header(Traces.Headers.PARENT_SPAN_ID, callState.getParentSpanId().get());
        }

        Response response;
        try {
            response = chain.proceed(instrumentedRequest.build());
        } finally {
            Traces.completeSpan();
        }

        return response;
    }
}
