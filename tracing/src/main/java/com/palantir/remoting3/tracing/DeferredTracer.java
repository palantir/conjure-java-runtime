/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.tracing;

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
    private final com.palantir.tracing.DeferredTracer delegate;

    public DeferredTracer() {
        this.delegate = new com.palantir.tracing.DeferredTracer();
    }

    /**
     * Runs the given callable with the current trace at
     * the time of construction of this {@link DeferredTracer}.
     */
    public <T, E extends Throwable> T withTrace(com.palantir.tracing.Tracers.ThrowingCallable<T, E> inner) throws E {
        return this.delegate.withTrace(inner);
    }
}
