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

package com.palantir.conjure.java.okhttp;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

/** A forwarding/delegating {@link okhttp3.Call}. Sub-classes should override individual methods. */
@SuppressWarnings({"checkstyle:noclone", "checkstyle:superclone"})
abstract class ForwardingCall implements Call {

    private final Call delegate;

    ForwardingCall(Call delegate) {
        this.delegate = delegate;
    }

    @Override
    public Request request() {
        return delegate.request();
    }

    @Override
    public Response execute() throws IOException {
        return delegate.execute();
    }

    @Override
    public void enqueue(Callback responseCallback) {
        delegate.enqueue(responseCallback);
    }

    @Override
    public void cancel() {
        delegate.cancel();
    }

    @Override
    public boolean isExecuted() {
        return delegate.isExecuted();
    }

    @Override
    public boolean isCanceled() {
        return delegate.isCanceled();
    }

    @Override
    public Call clone() {
        return doClone();
    }

    /**
     * Subclasses must provide a clone implementation. It typically returns a new instance of a subclass of {@link
     * ForwardingCall}. If calls have mutable internal state (e.g., counters, timeouts, etc.), then implementations
     * should copy the "current" internal state.
     */
    abstract Call doClone();

    protected Call getDelegate() {
        return delegate;
    }
}
