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

package com.palantir.remoting1.tracing;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;

/** Utility methods for making {@link ExecutorService} and {@link Runnable} instances tracing-aware. */
public final class Traces {
    private Traces() {}

    /** Returns a random ID suitable for span and trace IDs. */
    public static String randomId() {
        return Long.toHexString(ThreadLocalRandom.current().nextLong());
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
     * it's construction during its {@link TracingAwareCallable#call execution}. The {@link TracingAwareCallable}
     * constructor is typically called by a tracing-aware executor service on the same thread on which a user creates
     * the Callable; the {@link TracingAwareCallable#call} function is executed on an arbitrary (likely different)
     * thread with different {@link Trace tracing state}. In order to execute the task with the original (and
     * intuitively expected) tracing state, we remember the original state and set it for the duration of the {@link
     * TracingAwareCallable#call execution}.
     */
    public static <V> Callable<V> wrap(Callable<V> delegate) {
        return new TracingAwareCallable<>(delegate);
    }

    /** Like {@link #wrap(Callable)}, but for Runnables. */
    public static Runnable wrap(Runnable delegate) {
        return new TracingAwareRunnable(delegate);
    }

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
            Tracer.setTrace(trace);
            try {
                return delegate.call();
            } finally {
                Tracer.setTrace(originalTrace);
            }
        }
    }

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
            Tracer.setTrace(trace);
            try {
                delegate.run();
            } finally {
                Tracer.setTrace(originalTrace);
            }
        }
    }
}
