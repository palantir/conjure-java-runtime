/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.context;

import com.palantir.remoting3.context.ContextAwareTaskExecution.ThrowingCallable;
import com.palantir.remoting3.context.RequestContext.MappedContext;

/**
 * Utility class for capturing the current request context at time of construction, and then
 * running callables at some later time with that captured trace.
 * <pre>
 * <code>
 * DeferredContext deferredContext = new DeferredContext();
 *
 * //...
 *
 * // some time later
 * deferredContext.withContext(() -> {
 *     doThings();
 *     System.out.println(Tracer.getTraceId()); // prints trace id at time of construction of deferred tracer
 *     return null;
 * });
 *
 * </code>
 * </pre>
 */
public final class DeferredContext {
    private final MappedContext context;

    public DeferredContext(String... excluding) {
        context = RequestContext.currentContext().deepCopy(excluding);
    }

    /**
     * Runs the given callable with the current trace at
     * the time of construction of this {@link DeferredContext}.
     */
    public <T, E extends Throwable> T withContext(ThrowingCallable<T, E> inner) throws E {
        MappedContext original = RequestContext.currentContext();
        RequestContext.setContext(context.shallowCopy());
        try {
            return inner.call();
        } finally {
            RequestContext.setContext(original);
        }
    }
}
