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

import java.util.concurrent.Callable;

/**
 * Wraps a given callable such that its execution operates with the {@link RequestContext thread-local context} of the
 * thread that constructs the {@link ContextAwareCallable} instance rather than the thread that executes the callable.
 * <p>
 * The constructor is typically called by a context-aware executor service on the same thread on which a user
 * creates {@link Callable delegate}, and the {@link #call()} method is executed on an arbitrary (likely different)
 * thread with different {@link RequestContext context state}. In order to execute the task with the original (and
 * intuitively expected) context state, we remember the original state and set it for the duration of the
 * {@link #call() execution}, minus any keys explicitly excluded.
 */
class ContextAwareCallable<V> implements Callable<V> {
    private final Callable<V> delegate;
    private final DeferredContext deferredContext;

    ContextAwareCallable(Callable<V> delegate, String... excluding) {
        this.delegate = delegate;
        this.deferredContext = new DeferredContext(excluding);
    }

    @Override
    public V call() throws Exception {
        return this.deferredContext.withContext(delegate::call);
    }
}
