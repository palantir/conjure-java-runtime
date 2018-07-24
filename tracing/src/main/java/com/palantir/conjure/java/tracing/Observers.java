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

package com.palantir.conjure.java.tracing;

import com.palantir.conjure.java.api.tracing.Span;
import com.palantir.conjure.java.api.tracing.SpanObserver;
import java.util.concurrent.ExecutorService;

public final class Observers {

    private Observers() {}

    /**
     * Wraps the given observer into a {@link AsyncSpanObserver} that handles at most {@code maxInflights} number of
     * concurrent observations. Any additional concurrent observation is discarded and logged.
     */
    public static SpanObserver asyncDecorator(
            final SpanObserver observer, ExecutorService executorService, int maxInflights) {
        return new AsyncSpanObserver(executorService, maxInflights) {
            @Override
            public void doConsume(Span span) {
                observer.consume(span);
            }
        };
    }
}
