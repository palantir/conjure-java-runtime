/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.okhttp;

import com.palantir.tracing.CloseableSpan;
import com.palantir.tracing.DetachedSpan;
import com.palantir.tracing.OkhttpTraceInterceptor2;
import com.palantir.tracing.api.SpanType;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** An OkHttp interceptor that adds Zipkin-style trace/span/parent-span headers to the HTTP request. */
public final class OkhttpTraceInterceptor {

    /** The HTTP header used to communicate API endpoint names internally. Not considered public API. */
    public static final String PATH_TEMPLATE_HEADER = "hr-path-template";

    private static final Interceptor delegate = OkhttpTraceInterceptor2.create(OkhttpTraceInterceptor::createSpan);

    static final Interceptor INSTANCE = new Interceptor() {
        @Override
        public Response intercept(Chain chain) throws IOException {
            try {
                return delegate.intercept(chain);
            } finally {
                DetachedSpan waitForBody = chain.request().tag(AttemptSpan.class)
                        .attemptSpan()
                        .childDetachedSpan("OkHttp: wait-for-body", SpanType.CLIENT_OUTGOING);

                chain.request().tag(SettableWaitForBodySpan.class).setWaitForBodySpan(waitForBody);
            }
        }
    };

    @SuppressWarnings("MustBeClosedChecker") // the OkhttpTraceInterceptor2 will definitely close this
    private static CloseableSpan createSpan(Request request) {
        return request
                .tag(AttemptSpan.class)
                .attemptSpan()
                .childSpan("OkHttp: wait-for-headers", SpanType.CLIENT_OUTGOING);
    }

    private OkhttpTraceInterceptor() {}
}
