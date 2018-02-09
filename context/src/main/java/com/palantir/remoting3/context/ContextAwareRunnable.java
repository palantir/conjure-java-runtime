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

/**
 * Wraps a given runnable such that its execution operates with the {@link RequestContext thread-local context} of the
 * thread that constructs the {@link ContextAwareRunnable} instance rather than the thread that executes the runnable.
 */
class ContextAwareRunnable implements Runnable {
    private final Runnable delegate;
    private DeferredContext deferredContext;

    ContextAwareRunnable(Runnable delegate, String... excluding) {
        this.delegate = delegate;
        this.deferredContext = new DeferredContext(excluding);
    }

    @Override
    public void run() {
        deferredContext.withContext(() -> {
            delegate.run();
            return null;
        });
    }
}
