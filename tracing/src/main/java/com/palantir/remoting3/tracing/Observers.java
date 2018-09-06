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

package com.palantir.remoting3.tracing;

import com.palantir.remoting.api.tracing.Span;
import com.palantir.remoting.api.tracing.SpanObserver;
import java.util.concurrent.ExecutorService;

public final class Observers {

    private Observers() {}

    /**
     * Wraps the given observer into a {@link AsyncSpanObserver} that handles at most {@code maxInflights} number of
     * concurrent observations. Any additional concurrent observation is discarded and logged.
     */
    public static SpanObserver asyncDecorator(
            final SpanObserver observer, ExecutorService executorService, int maxInflights) {
        return new SpanObserver() {
            private final com.palantir.tracing.AsyncSpanObserver delegate =
                    new com.palantir.tracing.AsyncSpanObserver(executorService, maxInflights) {
                @Override
                public void doConsume(com.palantir.tracing.api.Span span) {
                    observer.asConjure().consume(span);
                }
            };

            @Override
            public void consume(Span span) {
                this.delegate.doConsume(span.asConjure());
            }

            @Override
            public com.palantir.tracing.api.SpanObserver asConjure() {
                return this.delegate;
            }
        };
    }
}
