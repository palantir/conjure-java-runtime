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

package com.palantir.remoting3.tracing;

import com.palantir.remoting.api.tracing.OpenSpan;
import com.palantir.tracing.ExposedTrace;
import com.palantir.tracing.TraceSampler;
import com.palantir.tracing.api.Span;
import com.palantir.tracing.api.SpanObserver;
import com.palantir.tracing.api.SpanType;

/** Utility functions to convert old remoting-api classes to the new tracing-java ones and vice-versa. */
public final class Convert {

    private Convert() {}

    static SpanType spanType(com.palantir.remoting.api.tracing.SpanType old) {
        if (old == null) {
            return null;
        }

        switch (old) {
            case SERVER_INCOMING:
                return SpanType.SERVER_INCOMING;
            case CLIENT_OUTGOING:
                return SpanType.CLIENT_OUTGOING;
            case LOCAL:
                return SpanType.LOCAL;
        }
        throw new IllegalStateException("Unable to convert: " + old);
    }

    static SpanObserver spanObserver(com.palantir.remoting.api.tracing.SpanObserver old) {
        if (old == null) {
            return null;
        }

        return new SpanObserver() {
            @Override
            public void consume(Span span) {
                old.consume(toRemotingSpan(span));
            }
        };
    }

    static com.palantir.remoting.api.tracing.Span toRemotingSpan(Span span) {
        if (span == null) {
            return null;
        }

        return com.palantir.remoting.api.tracing.Span.builder()
                .traceId(span.getTraceId())
                .parentSpanId(span.getParentSpanId())
                .spanId(span.getSpanId())
                .type(toRemotingSpanType(span.type()))
                .operation(span.getOperation())
                .startTimeMicroSeconds(span.getStartTimeMicroSeconds())
                .durationNanoSeconds(span.getDurationNanoSeconds())
                .build();
    }

    static OpenSpan toRemotingOpenSpan(com.palantir.tracing.api.OpenSpan openSpan) {
        if (openSpan == null) {
            return null;
        }

        return OpenSpan.builder()
                .parentSpanId(openSpan.getParentSpanId())
                .spanId(openSpan.getSpanId())
                .type(toRemotingSpanType(openSpan.type()))
                .operation(openSpan.getOperation())
                .startTimeMicroSeconds(openSpan.getStartTimeMicroSeconds())
                .startClockNanoSeconds(openSpan.getStartClockNanoSeconds())
                .build();
    }

    static com.palantir.remoting.api.tracing.SpanType toRemotingSpanType(SpanType type) {
        if (type == null) {
            return null;
        }

        switch (type) {
            case CLIENT_OUTGOING:
                return com.palantir.remoting.api.tracing.SpanType.CLIENT_OUTGOING;
            case SERVER_INCOMING:
                return com.palantir.remoting.api.tracing.SpanType.SERVER_INCOMING;
            case LOCAL:
                return com.palantir.remoting.api.tracing.SpanType.LOCAL;
        }

        throw new UnsupportedOperationException("Unable to convert to Remoting SpanType");
    }

    public static com.palantir.remoting.api.tracing.SpanObserver toRemotingSpanObserver(SpanObserver unsubscribe) {
        if (unsubscribe == null) {
            return null;
        }

        return new com.palantir.remoting.api.tracing.SpanObserver() {
            @Override
            public void consume(com.palantir.remoting.api.tracing.Span span) {
                unsubscribe.consume(Convert.span(span));
            }
        };
    }

    public static Trace toRemotingTrace(com.palantir.tracing.Trace newTrace) {
        if (newTrace == null) {
            return null;
        }

        // Warning - this is NOT a lossless copy - the newTrace actually contains a stack of OpenSpans, which we can't access.
        return new Trace(ExposedTrace.isObservable(newTrace), ExposedTrace.getTraceId(newTrace));
    }

    public static Span span(com.palantir.remoting.api.tracing.Span old) {
        if (old == null) {
            return null;
        }

        return Span.builder()
                .traceId(old.getTraceId())
                .parentSpanId(old.getParentSpanId())
                .spanId(old.getSpanId())
                .type(Convert.spanType(old.type()))
                .operation(old.getOperation())
                .startTimeMicroSeconds(old.getStartTimeMicroSeconds())
                .durationNanoSeconds(old.getDurationNanoSeconds())
                .metadata(old.getMetadata())
                .build();
    }

    public static TraceSampler traceSampler(com.palantir.remoting3.tracing.TraceSampler sampler) {
        if (sampler == null) {
            return null;
        }
        
        return new TraceSampler() {
            @Override
            public boolean sample() {
                return sampler.sample();
            }
        };
    }
}
