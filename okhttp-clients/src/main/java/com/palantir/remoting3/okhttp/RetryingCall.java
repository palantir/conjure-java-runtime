/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.okhttp;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public final class RetryingCall extends ForwardingCall {

    private final CallRetrier retryer;

    RetryingCall(Call delegate, CallRetrier retryer) {
        super(delegate);
        this.retryer = retryer;
    }

    @Override
    public Response execute() throws IOException {
        return retryer.executeWithRetry(getDelegate());
    }

    @Override
    public void enqueue(Callback responseCallback) {
        retryer.enqueueWithRetry(getDelegate(), responseCallback);
    }

    @Override
    public RetryingCall doClone() {
        return new RetryingCall(getDelegate().clone(), retryer);
    }
}
