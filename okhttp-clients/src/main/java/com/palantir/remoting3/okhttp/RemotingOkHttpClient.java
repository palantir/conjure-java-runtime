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

package com.palantir.remoting3.okhttp;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;

final class RemotingOkHttpClient extends ForwardingOkHttpClient {

    private static final int MAX_NUM_RELOCATIONS = 20;

    private final Supplier<BackoffStrategy> backoffStrategyFactory;
    private final UrlSelector urls;
    private final ScheduledExecutorService schedulingExecutor;
    private final ExecutorService executionExecutor;
    private final Duration timeout;

    RemotingOkHttpClient(
            OkHttpClient delegate,
            Supplier<BackoffStrategy> backoffStrategy,
            UrlSelector urls,
            ScheduledExecutorService schedulingExecutor,
            ExecutorService executionExecutor, Duration timeout) {
        super(delegate);
        this.backoffStrategyFactory = backoffStrategy;
        this.urls = urls;
        this.schedulingExecutor = schedulingExecutor;
        this.executionExecutor = executionExecutor;
        this.timeout = timeout;
    }

    @Override
    public Call newCall(Request request) {
        return newCallWithMutableState(request, backoffStrategyFactory.get(), MAX_NUM_RELOCATIONS);
    }

    Call newCallWithMutableState(Request request, BackoffStrategy backoffStrategy, int maxNumRelocations) {
        return new RemotingOkHttpCall(
                getDelegate().newCall(request),
                backoffStrategy,
                urls,
                this,
                schedulingExecutor,
                executionExecutor,
                timeout,
                maxNumRelocations);
    }
}
