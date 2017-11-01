/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting3.tracing;

import com.palantir.remoting3.tracing.Tracers.ThrowingCallable;

/**
 * Utility class for capturing the current trace at time of construction, and then
 * running callables at some later time with that captured trace.
 * <pre>
 * <code>
 * DeferredTracer deferredTracer = new DeferredTracer();
 *
 * //...
 *
 * // some time later
 * deferredTracer.withTrace(() -> {
 *     doThings();
 *     System.out.println(Tracer.getTraceId()); // prints trace id at time of construction of deferred tracer
 *     return null;
 * });
 *
 * </code>
 * </pre>
 */
public final class DeferredTracer {
    private final Trace trace;

    public DeferredTracer() {
        this.trace = Tracer.copyTrace();
    }

    /**
     * Runs the given callable with the current trace at
     * the time of construction of this {@link DeferredTracer}.
     */
    public <T, E extends Throwable> T withTrace(ThrowingCallable<T, E> inner) throws E {
        Trace originalTrace = Tracer.copyTrace();
        Tracer.setTrace(trace);
        try {
            return inner.call();
        } finally {
            Tracer.setTrace(originalTrace);
        }
    }
}
