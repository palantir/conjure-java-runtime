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

package com.palantir.remoting3.context;

import com.palantir.remoting3.context.RequestContext.MappedContext;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/** Utility methods for making {@link ExecutorService} and {@link java.lang.Runnable} instances context-aware. */
public final class ContextAwareTaskExecution {
    private ContextAwareTaskExecution() {}

    /**
     * Wraps the provided executor service such that any submitted {@link Callable} (or {@link Runnable}) is {@link
     * #wrap wrapped} in order to be context-aware.
     */
    public static ExecutorService wrap(ExecutorService executorService) {
        return new WrappingExecutorService(executorService, ContextAwareTaskExecution::wrap);
    }

    /**
     * Wraps the provided scheduled executor service to make submitted tasks maintain context, see {@link
     * #wrap(ScheduledExecutorService)}. This method should not be used to wrap a ScheduledExecutorService that has
     * already been {@link #wrapWithNewContext(ScheduledExecutorService)} wrapped with new context. If this is done, a
     * new context will be generated for each execution, effectively bypassing the intent of this method.
     */
    public static ScheduledExecutorService wrap(ScheduledExecutorService executorService) {
        return new WrappingScheduledExecutorService(executorService, ContextAwareTaskExecution::wrap);
    }

    /**
     * Wraps the given {@link Callable} such that it uses the thread-local {@link RequestContext state} at the time of
     * it's construction during its {@link Callable#call() execution}, minus any excluded keys.
     */
    public static <V> Callable<V> wrap(Callable<V> delegate, String... excluding) {
        return new ContextAwareCallable<>(delegate, excluding);
    }

    /** Like {@link #wrap(Callable, String...)}, but for Runnables. */
    public static Runnable wrap(Runnable delegate, String... excluding) {
        return new ContextAwareRunnable(delegate, excluding);
    }

    /**
     * Wraps the provided executor service such that any submitted {@link Callable} (or {@link Runnable}) is {@link
     * #wrap wrapped} in order to be context-aware.
     */
    public static ExecutorService wrapWithPartialContext(ExecutorService executorService, String... excluding) {
        return new WrappingExecutorService(executorService, new TaskWrapper() {
            @Override
            public <T> Callable<T> wrapTask(Callable<T> callable) {
                return wrap(callable, excluding);
            }
        });
    }

    /**
     * Wraps the given {@link Callable} such that it uses a copy of the {@link RequestContext context} at time of
     * construction, which excludes the given context keys.  That is, the context during its
     * {@link Callable#call() execution} will be separate from the context at construction or any context already set on
     * the thread used to execute the callable. Each execution of the callable will have a fresh copy of the context.
     */
    public static ScheduledExecutorService wrapWithPartialContext(ScheduledExecutorService executorService,
            String... excluding) {
        return new WrappingScheduledExecutorService(executorService, new TaskWrapper() {
            @Override
            public <T> Callable<T> wrapTask(Callable<T> callable) {
                return wrap(callable, excluding);
            }
        });
    }

    /**
     * Wraps the given {@link Callable} such that it creates a {@link RequestContext context} for its execution,
     * excluding any specified keys.  That is, the context during its {@link Callable#call() execution} is separate from
     * the context at construction or any context already set on the thread used to execute the callable.
     * Each execution of the callable will have a deepCopy of the context.
     */
    public static <V> Callable<V> wrapWithPartialContext(Callable<V> delegate, String... excluding) {
        return new ContextAwareCallable<>(delegate, excluding);
    }

    /**
     * Wraps the given {@link Callable} such that it creates a {@link RequestContext context} for its execution,
     * excluding any specified keys.  That is, the context during its {@link Callable#call() execution} is separate from
     * the context at construction or any context already set on the thread used to execute the callable.
     * Each execution of the callable will have a deepCopy of the context.
     */
    public static Runnable wrapWithPartialContext(Runnable delegate, String... excluding) {
        return new ContextAwareRunnable(delegate, excluding);
    }

    /**
     * Wraps the provided scheduled executor service to make submitted tasks with a fresh {@link RequestContext context}
     * for each execution, see {@link #wrapWithNewContext(ScheduledExecutorService)}. This method should not be used to
     * wrap a ScheduledExecutorService that has already been {@link #wrap(ScheduledExecutorService) wrapped}. If this is
     * done, a new context will be generated for each execution, effectively bypassing the intent of the previous
     * wrapping.
     */
    public static ScheduledExecutorService wrapWithNewContext(ScheduledExecutorService executorService) {
        return new WrappingScheduledExecutorService(executorService, ContextAwareTaskExecution::wrapWithNewContext);
    }

    /**
     * Wraps the given {@link Callable} such that it creates a fresh {@link RequestContext context} for its execution.
     * That is, the context during its {@link Callable#call() execution} is entirely separate from the context at
     * construction or any context already set on the thread used to execute the callable. Each execution of the
     * callable will have a fresh context.
     */
    public static <V> Callable<V> wrapWithNewContext(Callable<V> delegate) {
        return () -> {
            // clear the existing context and keep it around for restoration when we're done
            MappedContext context = RequestContext.getAndClear();

            try {
                return delegate.call();
            } finally {
                // restore the context
                RequestContext.setContext(context);
            }
        };
    }

    /**
     * Wraps the given {@link Callable} such that it creates a fresh {@link RequestContext context} for its execution.
     * That is, the context during its {@link Callable#call() execution} is entirely separate from the context at
     * construction or any context already set on the thread used to execute the callable. Each execution of the
     * callable will have a fresh context.
     */
    public static Runnable wrapWithNewContext(Runnable delegate) {
        return () -> {
            // clear the existing context and keep it around for restoration when we're done
            MappedContext context = RequestContext.getAndClear();

            try {
                delegate.run();
            } finally {
                // restore the context
                RequestContext.setContext(context);
            }
        };
    }

    public interface ThrowingCallable<T, E extends Throwable> {
        T call() throws E;
    }
}
