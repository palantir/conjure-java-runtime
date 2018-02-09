/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.remoting3.context.ContextAwareTaskExecution;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;

/** Utility methods for making {@link ExecutorService} and {@link Runnable} instances tracing-aware. */
public final class Tracers {
    /** The key under which trace ids are inserted into SLF4J {@link org.slf4j.MDC MDCs}. */
    public static final String TRACE_ID_KEY = "traceId";
    private static final char[] HEX_DIGITS =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private Tracers() {}

    /** Returns a random ID suitable for span and trace IDs. */
    public static String randomId() {
        return longToPaddedHex(ThreadLocalRandom.current().nextLong());
    }

    /**
     * Convert a long to a big-endian hex string. Hand-coded implementation is more efficient than
     * Strings.pad(Long.toHexString) because that code has to deal with mixed length longs, and then mixed length
     * amounts of padding - we want to minimise the overhead of tracing.
     */
    static String longToPaddedHex(long number) {
        char[] data = new char[16];
        data[0] = HEX_DIGITS[(int) ((number >> 60) & 0xF)];
        data[1] = HEX_DIGITS[(int) ((number >> 56) & 0xF)];
        data[2] = HEX_DIGITS[(int) ((number >> 52) & 0xF)];
        data[3] = HEX_DIGITS[(int) ((number >> 48) & 0xF)];
        data[4] = HEX_DIGITS[(int) ((number >> 44) & 0xF)];
        data[5] = HEX_DIGITS[(int) ((number >> 40) & 0xF)];
        data[6] = HEX_DIGITS[(int) ((number >> 36) & 0xF)];
        data[7] = HEX_DIGITS[(int) ((number >> 32) & 0xF)];
        data[8] = HEX_DIGITS[(int) ((number >> 28) & 0xF)];
        data[9] = HEX_DIGITS[(int) ((number >> 24) & 0xF)];
        data[10] = HEX_DIGITS[(int) ((number >> 20) & 0xF)];
        data[11] = HEX_DIGITS[(int) ((number >> 16) & 0xF)];
        data[12] = HEX_DIGITS[(int) ((number >> 12) & 0xF)];
        data[13] = HEX_DIGITS[(int) ((number >> 8) & 0xF)];
        data[14] = HEX_DIGITS[(int) ((number >> 4) & 0xF)];
        data[15] = HEX_DIGITS[(int) ((number >> 0) & 0xF)];
        return new String(data);
    }

    /**
     * Wraps the provided executor service such that any submitted {@link Callable} (or {@link Runnable}) is {@link
     * #wrap wrapped} in order to be trace-aware.
     *
     * @deprecated use {@link ContextAwareTaskExecution#wrap(ExecutorService)}
     */
    @Deprecated
    public static ExecutorService wrap(ExecutorService executorService) {
        return ContextAwareTaskExecution.wrap(executorService);
    }

    /**
     * Wraps the provided scheduled executor service to make submitted tasks traceable, see {@link
     * #wrap(ScheduledExecutorService)}. This method should not be used to wrap a ScheduledExecutorService that has
     * already been {@link #wrapWithNewTrace(ScheduledExecutorService) wrapped with new trace}. If this is done, a new
     * trace will be generated for each execution, effectively bypassing the intent of this method.
     *
     * @deprecated use {@link ContextAwareTaskExecution#wrap(ScheduledExecutorService)}
     */
    @Deprecated
    public static ScheduledExecutorService wrap(ScheduledExecutorService executorService) {
        return ContextAwareTaskExecution.wrap(executorService);
    }

    /**
     * Wraps the given {@link Callable} such that it uses the thread-local {@link Trace tracing state} at the time of
     * it's construction during its {@link Callable#call() execution}.
     *
     * @deprecated use {@link ContextAwareTaskExecution#wrap(Callable, String...)}
     */
    @Deprecated
    public static <V> Callable<V> wrap(Callable<V> delegate) {
        return ContextAwareTaskExecution.wrap(delegate);
    }

    /**
     * Like {@link #wrap(Callable)}, but for Runnables.
     *
     * @deprecated use {@link ContextAwareTaskExecution#wrap(Runnable, String...)}
     */
    @Deprecated
    public static Runnable wrap(Runnable delegate) {
        return ContextAwareTaskExecution.wrap(delegate);
    }

    /**
     * Wraps the provided scheduled executor service to make submitted tasks traceable with a fresh {@link Trace trace}
     * for each execution, see {@link #wrapWithNewTrace(ScheduledExecutorService)}. This method should not be used to
     * wrap a ScheduledExecutorService that has already been {@link #wrap(ScheduledExecutorService) wrapped}. If this is
     * done, a new trace will be generated for each execution, effectively bypassing the intent of the previous
     * wrapping.
     *
     * @deprecated use {@link ContextAwareTaskExecution#wrapWithNewContext(ScheduledExecutorService)}, or
     * {@link ContextAwareTaskExecution#wrapWithPartialContext(ScheduledExecutorService, String...)} for more
     * fine-grained control.
     */
    @Deprecated
    public static ScheduledExecutorService wrapWithNewTrace(ScheduledExecutorService executorService) {
        return ContextAwareTaskExecution.wrapWithNewContext(executorService);
    }

    /**
     * Wraps the given {@link Callable} such that it creates a fresh {@link Trace tracing state} for its execution.
     * That is, the trace during its {@link Callable#call() execution} is entirely separate from the trace at
     * construction or any trace already set on the thread used to execute the callable. Each execution of the callable
     * will have a fresh trace.
     *
     * @deprecated use {@link ContextAwareTaskExecution#wrapWithNewContext(Callable)} or
     * {@link ContextAwareTaskExecution#wrapWithPartialContext(Callable, String...)} for more fine-grained control
     */
    @Deprecated
    public static <V> Callable<V> wrapWithNewTrace(Callable<V> delegate) {
        return ContextAwareTaskExecution.wrapWithNewContext(delegate);
    }

    /**
     * Like {@link #wrapWithNewTrace(Callable)}, but for Runnables.
     *
     * @deprecated use {@link ContextAwareTaskExecution#wrapWithNewContext(Runnable)} or
     * {@link ContextAwareTaskExecution#wrapWithPartialContext(Runnable, String...)} for more fine-grained control
     */
    @Deprecated
    public static Runnable wrapWithNewTrace(Runnable delegate) {
        return ContextAwareTaskExecution.wrapWithNewContext(delegate);
    }

    /**
     * @deprecated use {@link ContextAwareTaskExecution.ThrowingCallable}
     */
    @Deprecated
    public interface ThrowingCallable<T, E extends Throwable> {
        T call() throws E;
    }
}
