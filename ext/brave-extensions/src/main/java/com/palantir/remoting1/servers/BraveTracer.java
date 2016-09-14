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

import com.github.kristofa.brave.AnnotationSubmitter;
import com.github.kristofa.brave.Brave;
import java.util.concurrent.TimeUnit;

public final class BraveTracer implements Tracer {

    private final Brave brave;

    BraveTracer(Brave brave) {
        this.brave = brave;
    }

    public Brave getBrave() {
        return brave;
    }

    @Override
    public BraveContext begin(String component, String operation) {
        return BraveContext.beginLocalTrace(brave, component, operation);
    }

    @Override
    public Class<? extends TraceContext> getContextClass() {
        return BraveContext.class;
    }

    @Override
    public String toString() {
        return "BraveTracer{brave=" + brave + '}';
    }

    public static AnnotationSubmitter.Clock defaultClock() {
        return DefaultClock.INSTANCE;
    }

    private enum DefaultClock implements AnnotationSubmitter.Clock {
        INSTANCE {
            @Override
            public long currentTimeMicroseconds() {
                return TimeUnit.MICROSECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

}
