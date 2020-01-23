/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.tracing.DetachedSpan;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SpanTerminatingInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(SpanTerminatingInterceptor.class);
    static final Interceptor INSTANCE = new SpanTerminatingInterceptor();

    private SpanTerminatingInterceptor() {}

    @Override
    public Response intercept(Chain chain) throws IOException {
        Tags.AttemptSpan attemptSpanTag = chain.request().tag(Tags.AttemptSpan.class);
        if (attemptSpanTag == null) {
            return chain.proceed(chain.request());
        }
        DetachedSpan attemptSpan = attemptSpanTag.attemptSpan();
        DetachedSpan dispatcherSpan =
                chain.request().tag(Tags.SettableDispatcherSpan.class).dispatcherSpan();

        if (attemptSpan == null || dispatcherSpan == null) {
            log.warn("Missing attemptSpan / dispatcherSpan, which will result in missing spans. Likely a "
                    + "conjure-java-runtime bug");
            return chain.proceed(chain.request());
        }

        dispatcherSpan.complete();
        try {
            return chain.proceed(chain.request());
        } finally {
            attemptSpan.complete();
            chain.request()
                    .tag(Tags.SettableWaitForBodySpan.class)
                    .waitForBodySpan()
                    .complete();
        }
    }
}
