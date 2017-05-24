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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Request;
import retrofit2.Call;

public final class DefaultAsyncCallTracker implements AsyncCallTracker {
    private final Set<UUID> asyncCalls = ConcurrentHashMap.newKeySet();

    @Override
    public <T> void registerAsyncCall(Call<T> call) {
        asyncCalls.add((UUID) call.request().tag());
    }

    @Override
    public boolean isAsyncRequest(Request request) {
        // type is correct...
        return asyncCalls.remove(request.tag());
    }
}
