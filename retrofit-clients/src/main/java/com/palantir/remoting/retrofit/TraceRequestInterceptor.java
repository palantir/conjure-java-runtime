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

package com.palantir.remoting.retrofit;

import com.palantir.tracing.TraceState;
import com.palantir.tracing.Traces;
import retrofit.RequestInterceptor;

public final class TraceRequestInterceptor implements RequestInterceptor {

    @Override
    public void intercept(RequestFacade request) {
        TraceState callState = Traces.deriveTrace("");
        request.addHeader(Traces.Headers.TRACE_ID, callState.getTraceId());
        if (callState.getParentSpanId().isPresent()) {
            request.addHeader(Traces.Headers.PARENT_SPAN_ID, callState.getParentSpanId().get());
        }
        request.addHeader(Traces.Headers.SPAN_ID, callState.getSpanId());
    }

}
