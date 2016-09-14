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

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.SpanId;
import com.google.common.base.Preconditions;

public final class BraveContext implements Tracer.TraceContext {

    private final Brave brave;
    private final SpanId spanId;

    @SuppressWarnings("WeakerAccess") // public API
    public static BraveContext beginLocalTrace(Brave brave, String component, String operation) {
        return new BraveContext(brave, brave.localTracer().startNewSpan(component, operation));
    }

    private BraveContext(Brave brave, SpanId spanId) {
        this.brave = Preconditions.checkNotNull(brave, "brave");
        this.spanId = Preconditions.checkNotNull(spanId, "spanId");
    }

    public Brave getBrave() {
        return brave;
    }

    public SpanId getSpanId() {
        return spanId;
    }

    @Override
    public void close() {
        getBrave().localTracer().finishSpan();
    }

}
