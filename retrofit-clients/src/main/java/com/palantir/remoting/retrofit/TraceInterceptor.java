/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.retrofit;

import com.palantir.tracing.TraceState;
import com.palantir.tracing.Traces;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;

public final class TraceInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        // instrument request
        TraceState callState = Traces.deriveTrace("");
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
            // complete response
            Traces.complete();
        }

        return response;
    }

}
