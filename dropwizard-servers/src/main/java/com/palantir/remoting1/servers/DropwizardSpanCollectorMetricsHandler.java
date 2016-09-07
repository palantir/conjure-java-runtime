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

package com.palantir.remoting1.servers;

import com.codahale.metrics.Counter;
import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.google.common.base.Preconditions;

public final class DropwizardSpanCollectorMetricsHandler implements SpanCollectorMetricsHandler {

    private final Counter acceptedCounter;
    private final Counter droppedCounter;

    public DropwizardSpanCollectorMetricsHandler(Counter acceptedCounter, Counter droppedCounter) {
        this.acceptedCounter = Preconditions.checkNotNull(acceptedCounter, "acceptedCounter");
        this.droppedCounter = Preconditions.checkNotNull(droppedCounter, "droppedCounter");
    }

    @Override
    public void incrementAcceptedSpans(int quantity) {
        acceptedCounter.inc(quantity);
    }

    @Override
    public void incrementDroppedSpans(int quantity) {
        droppedCounter.inc(quantity);
    }
}
