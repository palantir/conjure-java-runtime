/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package feign;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.palantir.tracing.TraceState;
import com.palantir.tracing.Traces;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Type;

public final class TraceResponseDecoder implements Decoder {

    private final Decoder delegate;

    public TraceResponseDecoder(Decoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, DecodeException, FeignException {
        String traceId = safeGetOnlyElement(response.headers().get(Traces.Headers.TRACE_ID), null);
        String spanId = safeGetOnlyElement(response.headers().get(Traces.Headers.SPAN_ID), null);
        Optional<TraceState> trace = Traces.getTrace();
        if (traceId != null && spanId != null && trace.isPresent()) {
            // there exists a trace, and the response included tracing information, so check the returned trace
            // matches our current trace
            if (trace.get().getTraceId().equals(traceId)
                    && trace.get().getSpanId().equals(spanId)) {
                // this trace is for the traceId and spanId on top of the tracing stack, complete it
                Traces.completeSpan();
            }
        }
        return delegate.decode(response, type);
    }

    private static <T> T safeGetOnlyElement(Iterable<T> iterable, T defaultValue) {
        return iterable == null ? defaultValue : Iterables.getOnlyElement(iterable, defaultValue);
    }

}
