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

import com.palantir.conjure.java.client.config.ImmutablesStyle;
import com.palantir.tracing.DetachedSpan;
import org.immutables.value.Value;

@Value.Immutable
@ImmutablesStyle
public interface AttemptSpan {
    @Value.Default
    default int attemptNumber() {
        return 0;
    }

    DetachedSpan attemptSpan();

    static AttemptSpan createAttempt(DetachedSpan entireSpan, int attemptNumber) {
        return ImmutableAttemptSpan.builder()
                .attemptNumber(attemptNumber)
                .attemptSpan(entireSpan.childDetachedSpan("OkHttp: Attempt " + attemptNumber))
                .build();
    }

    default AttemptSpan nextAttempt(DetachedSpan entireSpan) {
        return createAttempt(entireSpan, attemptNumber() + 1);
    }
}
