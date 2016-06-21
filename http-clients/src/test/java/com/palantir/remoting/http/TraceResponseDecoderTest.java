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

package com.palantir.remoting.http;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.palantir.tracing.Span;
import com.palantir.tracing.TraceState;
import com.palantir.tracing.Traces;
import com.palantir.tracing.Traces.Subscriber;
import feign.FeignException;
import feign.Response;
import feign.TraceResponseDecoder;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import org.hamcrest.CustomMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class TraceResponseDecoderTest {

    private CapturingSubscriber subscriber;
    private TraceState state;

    @Before
    public void before() {
        subscriber = new CapturingSubscriber();
        Traces.subscribe(subscriber);

        state = TraceState.builder()
                .traceId("traceId")
                .spanId("spanId")
                .operation("any")
                .build();
        Traces.setTrace(state);
    }

    @After
    public void after() {
        Traces.unsubscribe(subscriber);
    }

    @Test
    public void testDecode_completesMatchingSpan() throws DecodeException, FeignException, IOException {
        testDecodeInternal("traceId", "spanId");

        assertThat(subscriber.getObservedSpans(), hasItem(trace("traceId", "spanId")));
        assertThat(subscriber.getObservedSpans(), hasSize(1));

        assertThat(Traces.getTrace(), is(Optional.<TraceState>absent()));
    }

    @Test
    public void testDecode_doesNotPopNonMatchingSpan() throws DecodeException, FeignException, IOException {
        testDecodeInternal("traceId", "otherSpanId");

        assertThat(subscriber.getObservedSpans(), hasSize(0));

        assertThat(Traces.getTrace(), is(Optional.of(state)));
    }

    @Test
    public void testDecode_doesNotPopNonMatchingTrace() throws DecodeException, FeignException, IOException {
        testDecodeInternal("otherTraceId", "spanId");

        assertThat(subscriber.getObservedSpans(), hasSize(0));

        assertThat(Traces.getTrace(), is(Optional.of(state)));
    }

    private synchronized void testDecodeInternal(String traceId, String spanId) throws IOException {
        Decoder decoder = new TraceResponseDecoder(mock(Decoder.class));
        Response response = Response.create(200,
                "",
                ImmutableMap.of(
                        Traces.Headers.TRACE_ID, (Collection<String>) ImmutableSet.of(traceId),
                        Traces.Headers.SPAN_ID, (Collection<String>) ImmutableSet.of(spanId)),
                new byte[0]);

        decoder.decode(response, mock(Type.class));
    }

    private static class CapturingSubscriber implements Subscriber {
        private final List<Span> observedSpans = Lists.newArrayList();

        @Override
        public void consume(Span span) {
            observedSpans.add(span);
        }

        public List<Span> getObservedSpans() {
            return ImmutableList.copyOf(observedSpans);
        }
    }

    private static class TraceMatcher extends CustomMatcher<Span> {
        private final String traceId;
        private final String spanId;

        TraceMatcher(String traceId, String spanId) {
            super("Tests whether a Span is a traceId and spanId match to a TraceState");
            this.traceId = traceId;
            this.spanId = spanId;
        }

        @Override
        public boolean matches(Object item) {
            if (item instanceof Span) {
                Span input = (Span) item;
                return input.getTraceId().equals(traceId) && input.getSpanId().equals(spanId);
            }
            return false;
        }
    }

    public static TraceMatcher trace(String traceId, String spanId) {
        return new TraceMatcher(traceId, spanId);
    }

}
