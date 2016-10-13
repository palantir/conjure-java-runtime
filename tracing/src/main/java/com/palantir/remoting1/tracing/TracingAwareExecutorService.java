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

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TracingAwareExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    public TracingAwareExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override
    public final void shutdown() {
        delegate.shutdown();
    }

    @Override
    public final List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public final boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public final boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public final boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public final <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(new TracingAwareCallable<>(task));
    }

    @Override
    public final <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(new TracingAwareRunnable(task), result);
    }

    @Override
    public final Future<?> submit(Runnable task) {
        return delegate.submit(new TracingAwareRunnable(task));
    }

    @Override
    public final <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(Collections2.transform(tasks, new TracedCallableConverter<T>()));
    }

    @Override
    public final <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(Collections2.transform(tasks, new TracedCallableConverter<T>()), timeout, unit);
    }

    @Override
    public final <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(Collections2.transform(tasks, new TracedCallableConverter<T>()));
    }

    @Override
    public final <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(Collections2.transform(tasks, new TracedCallableConverter<T>()), timeout, unit);
    }

    @Override
    public final void execute(Runnable command) {
        delegate.execute(new TracingAwareRunnable(command));
    }

    private static class TracedCallableConverter<T> implements Function<Callable<T>, Callable<T>> {
        @Override
        public Callable<T> apply(Callable<T> input) {
            return new TracingAwareCallable<>(input);
        }
    }
}
