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

/**
 * The mechanism (as of 2.5.0) that http-remoting uses in order to throw a RemoteException on failure relies on throwing
 * in the OkHttp interceptor {@link SerializableErrorInterceptor}. This bubbles up the stack until it reaches the
 * caller.
 * <p>
 * Unfortunately, this doesn't work when using the Retrofit async features; the exception is thrown in a different
 * thread and the callback never runs.
 * <p>
 * Furthermore, Retrofit provides no obvious plugin point where one can switch this kind of behaviour on a per request
 * basis. Ideally we'd get rid of the {@code Call} returning methods since the {@link
 * java.util.concurrent.CompletableFuture} methods entirely supercede; but this would be a breaking API change.
 * <p>
 * So, we have this interface. The intended use is that all calls are tagged with an {@link AsyncCallTag}. If the
 * request is to be called in an async fashion, {@link #setCallAsync()} is called.
 * <p>
 * The interceptor will call {@link #isAsyncCall()}, which will return true iff it was registered in this way. In this
 * case, the interceptor is a no-op.
 * <p>
 * The {@code AsyncSerializableErrorCallAdapterFactory} (in the retrofit2-clients project) then handles filling the
 * exception into the response.
 */
public final class AsyncCallTag {
    private volatile boolean isAsyncCall = false;

    public boolean isAsyncCall() {
        return isAsyncCall;
    }

    public void setCallAsync() {
        isAsyncCall = true;
    }
}
