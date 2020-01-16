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
import java.util.function.Supplier;
import org.immutables.value.Value;

/**
 * When constructing a brand new {@link okhttp3.Request}, we can save typed objects that can be accessed later in a
 * typed way from event listeners, interceptors and callbacks.
 *
 * <p>The 'SettableX' pattern is necessary because we need to store a span, but don't actually want to start it just
 * yet, so these containers contain null initially and are populated later on.
 */
final class Tags {

    interface EntireSpan extends Supplier<DetachedSpan> {}

    @Value.Immutable
    @ImmutablesStyle
    interface AttemptSpan {
        @Value.Default
        default int attemptNumber() {
            return 0;
        }

        DetachedSpan attemptSpan();

        static AttemptSpan createAttempt(DetachedSpan entireSpan, int attemptNumber) {
            return ImmutableAttemptSpan.builder()
                    .attemptNumber(attemptNumber)
                    .attemptSpan(entireSpan.childDetachedSpan("OkHttp: attempt " + attemptNumber))
                    .build();
        }

        default AttemptSpan nextAttempt(DetachedSpan entireSpan) {
            return createAttempt(entireSpan, attemptNumber() + 1);
        }
    }

    @Value.Modifiable
    @ImmutablesStyle
    interface SettableDispatcherSpan {

        DetachedSpan dispatcherSpan();

        SettableDispatcherSpan setDispatcherSpan(DetachedSpan span);

        static SettableDispatcherSpan create() {
            return ModifiableSettableDispatcherSpan.create();
        }
    }

    @Value.Modifiable
    @ImmutablesStyle
    interface SettableWaitForBodySpan {
        DetachedSpan waitForBodySpan();

        SettableWaitForBodySpan setWaitForBodySpan(DetachedSpan span);

        static SettableWaitForBodySpan create() {
            return ModifiableSettableWaitForBodySpan.create();
        }
    }

    private Tags() {}
}
