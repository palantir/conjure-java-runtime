/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.palantir.tracing.TraceState;
import com.palantir.tracing.Traces;
import feign.FeignException;
import feign.Response;
import feign.TraceResponseDecoder;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import org.junit.Test;

public final class TraceResponseDecoderTest {

    @Test
    public void testDecode_completesMatchingSpan() throws DecodeException, FeignException, IOException {
        TraceState state = TraceState.builder()
                .traceId("traceId")
                .spanId("spanId")
                .operation("any")
                .build();
        Traces.setTrace(state);

        testDecodeInternal("traceId", "spanId");

        assertThat(Traces.getTrace(), is(Optional.<TraceState>absent()));
    }

    @Test
    public void testDecode_doesNotPopNonMatchingSpan() throws DecodeException, FeignException, IOException {
        TraceState state = TraceState.builder()
                .traceId("traceId")
                .spanId("spanId")
                .operation("any")
                .build();
        Traces.setTrace(state);

        testDecodeInternal("traceId", "otherSpanId");

        assertThat(Traces.getTrace(), is(Optional.of(state)));
    }

    @Test
    public void testDecode_doesNotPopNonMatchingTrace() throws DecodeException, FeignException, IOException {
        TraceState state = TraceState.builder()
                .traceId("traceId")
                .spanId("spanId")
                .operation("any")
                .build();
        Traces.setTrace(state);

        testDecodeInternal("otherTraceId", "spanId");

        assertThat(Traces.getTrace(), is(Optional.of(state)));
    }

    private void testDecodeInternal(String traceId, String spanId) throws IOException {
        Decoder decoder = new TraceResponseDecoder(mock(Decoder.class));
        Response response = Response.create(200,
                "",
                ImmutableMap.of(
                        Traces.Headers.TRACE_ID, (Collection<String>) ImmutableSet.of(traceId),
                        Traces.Headers.SPAN_ID, (Collection<String>) ImmutableSet.of(spanId)),
                new byte[0]);

        decoder.decode(response, mock(Type.class));
    }

}
