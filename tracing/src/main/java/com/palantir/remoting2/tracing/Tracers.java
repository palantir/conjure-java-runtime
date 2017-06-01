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

package com.palantir.remoting2.tracing;

import com.google.common.base.Strings;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.MDC;

/** Utility methods for making {@link ExecutorService} and {@link Runnable} instances tracing-aware. */
public final class Tracers {
    /** The key under which trace ids are inserted into SLF4J {@link org.slf4j.MDC MDCs}. */
    static final String MDC_KEY = "traceId";

    private Tracers() {}

    /** Returns a random ID suitable for span and trace IDs. */
    public static String randomId() {
        return longToPaddedHex(ThreadLocalRandom.current().nextLong());
    }

    static String longToPaddedHex(long number) {
        return Strings.padStart(Long.toHexString(number), 16, '0');
    }

    /**
     * Wraps the provided executor service such that any submitted {@link Callable} (or {@link Runnable}) is {@link
     * #wrap wrapped} in order to be trace-aware.
     */
    public static ExecutorService wrap(ExecutorService executorService) {
        return new WrappingExecutorService(executorService) {
            @Override
            protected <T> Callable<T> wrapTask(Callable<T> callable) {
                return wrap(callable);
            }
        };
    }

    /**
     * Wraps the provided scheduled executor service to make submitted tasks traceable, see {@link
     * #wrap(ScheduledExecutorService)}.
     */
    public static ScheduledExecutorService wrap(ScheduledExecutorService executorService) {
        return new WrappingScheduledExecutorService(executorService) {
            @Override
            protected <T> Callable<T> wrapTask(Callable<T> callable) {
                return wrap(callable);
            }
        };
    }

    /**
     * Wraps the given {@link Callable} such that it uses the thread-local {@link Trace tracing state} at the time of
     * it's construction during its {@link Callable#call() execution}.
     */
    public static <V> Callable<V> wrap(Callable<V> delegate) {
        return new TracingAwareCallable<>(delegate);
    }

    /** Like {@link #wrap(Callable)}, but for Runnables. */
    public static Runnable wrap(Runnable delegate) {
        return new TracingAwareRunnable(delegate);
    }

    /**
     * Wraps a given callable such that its execution operates with the {@link Trace thread-local Trace} of the thread
     * that constructs the {@link TracingAwareCallable} instance rather than the thread that executes the callable.
     * <p>
     * The constructor is typically called by a tracing-aware executor service on the same thread on which a user
     * creates {@link Callable delegate}, and the {@link #call()} method is executed on an arbitrary (likely different)
     * thread with different {@link Trace tracing state}. In order to execute the task with the original (and
     * intuitively expected) tracing state, we remember the original state and set it for the duration of the
     * {@link #call() execution}.
     */
    private static class TracingAwareCallable<V> implements Callable<V> {
        private final Callable<V> delegate;
        private final Trace trace;

        TracingAwareCallable(Callable<V> delegate) {
            this.delegate = delegate;
            this.trace = Tracer.copyTrace();
        }

        @Override
        public V call() throws Exception {
            Trace originalTrace = Tracer.copyTrace();
            String originalMdcTraceIdValue = MDC.get(MDC_KEY);
            Tracer.setTrace(trace);
            MDC.put(MDC_KEY, trace.getTraceId());
            try {
                return delegate.call();
            } finally {
                Tracer.setTrace(originalTrace);
                MDC.put(MDC_KEY, originalMdcTraceIdValue);
            }
        }
    }

    /**
     * Wraps a given runnable such that its execution operates with the {@link Trace thread-local Trace} of the thread
     * that constructs the {@link TracingAwareRunnable} instance rather than the thread that executes the runnable.
     */
    private static class TracingAwareRunnable implements Runnable {
        private final Runnable delegate;
        private final Trace trace;

        TracingAwareRunnable(Runnable delegate) {
            this.delegate = delegate;
            this.trace = Tracer.copyTrace();
        }

        @Override
        public void run() {
            Trace originalTrace = Tracer.copyTrace();
            String originalMdcTraceIdValue = MDC.get(MDC_KEY);
            Tracer.setTrace(trace);
            MDC.put(MDC_KEY, trace.getTraceId());
            try {
                delegate.run();
            } finally {
                Tracer.setTrace(originalTrace);
                MDC.put(MDC_KEY, originalMdcTraceIdValue);
            }
        }
    }
}
