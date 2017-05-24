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

package com.palantir.remoting2.retrofit2;

import okhttp3.Request;
import retrofit2.Call;

/**
 * The mechanism (as of 2.5.0) that http-remoting uses in order to throw a RemoteException on failure
 * relies on throwing in the OkHttp interceptor {@link com.palantir.remoting2.retrofit2.SerializableErrorInterceptor}.
 * This bubbles up the stack until it reaches the caller.
 *
 * Unfortunately, this doesn't work when using the Retrofit async features; the exception is thrown in a different
 * thread and the callback never runs.
 *
 * Furthermore, Retrofit provides no obvious plugin point where one can switch this kind of behaviour on a per request
 * basis. Ideally we'd get rid of the {@link Call} returning methods since the
 * {@link java.util.concurrent.CompletableFuture} methods entirely supercede; but this would be a breaking API change.
 *
 * So, we have this interface. The intended use is that a {@link Call}'s request, if it is to be run in an async
 * fashion, is tagged with a {@link java.util.UUID} and passed to {@link #registerAsyncCall(Call)}.
 *
 * The interceptor will call {@link #isAsyncRequest(Request)}, which will return true iff it was registered in this
 * way. In this case, the interceptor is a no-op.
 *
 * The {@link AsyncSerializableErrorCallAdapterFactory} then handles filling the exception into the response.
 *
 * There are currently two tests for correctness here; one tests that a non-async call throws correctly, the other
 * tests that an async call throws correctly.
 */
public interface AsyncCallTracker {
    <T> void registerAsyncCall(Call<T> call);
    boolean isAsyncRequest(Request request);
}
