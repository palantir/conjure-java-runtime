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

import com.google.common.annotations.VisibleForTesting;
import com.palantir.remoting3.clients.ClientConfiguration;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import okhttp3.Call;

/**
 * Constructs instances of {@link AsyncQosIoExceptionHandler} given a {@link UrlSelector} and an
 * {@link okhttp3.Call.Factory}. These parameters typically cannot be generated independent of the underlying
 * {@link okhttp3.OkHttpClient} that is used.
 */
final class AsyncQosIoExceptionHandlerFactory implements QosIoExceptionHandlerProvider {
    private final ScheduledExecutorService retryExecutorService;
    private final ExecutorService dispatcherExecutorService;
    private final Supplier<BackoffStrategy> backoffStrategySupplier;

    @VisibleForTesting
    AsyncQosIoExceptionHandlerFactory(
            ScheduledExecutorService retryExecutorService,
            ExecutorService dispatcherExecutorService,
            Supplier<BackoffStrategy> backoffStrategySupplier) {
        this.retryExecutorService = retryExecutorService;
        this.dispatcherExecutorService = dispatcherExecutorService;
        this.backoffStrategySupplier = backoffStrategySupplier;
    }

    AsyncQosIoExceptionHandlerFactory(
            ScheduledExecutorService retryExecutorService,
            ExecutorService dispatcherExecutorService,
            ClientConfiguration config) {
        this(retryExecutorService,
                dispatcherExecutorService,
                () -> new ExponentialBackoff(config.maxNumRetries(), config.backoffSlotSize(), new Random()));
    }

    @Override
    public QosIoExceptionHandler createHandler(UrlSelector urlSelector, Call.Factory callFactory) {
        return new AsyncQosIoExceptionHandler(
                retryExecutorService,
                dispatcherExecutorService,
                backoffStrategySupplier.get(),
                new MultiServerRequestCreator(urlSelector),
                callFactory);
    }
}
