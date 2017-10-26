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

import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public final class InterruptibleCall extends ForwardingCall {

    public InterruptibleCall(Call delegate) {
        super(delegate);
    }

    @Override
    public Response execute() throws IOException {
        SettableFuture<Response> future = SettableFuture.create();

        getDelegate().enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                future.set(response);
            }
        });

        try {
            return future.get();
        } catch (InterruptedException e) {
            getDelegate().cancel();
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread was interrupted, cancelling call", e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    Call doClone() {
        return null;
    }
}
