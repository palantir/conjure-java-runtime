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
 * Constructs instances of {@link AsyncQosIoExceptionHandler} given a {@link okhttp3.Call.Factory}, which is often
 * provided at a substantially later time (e.g. after constructing an {@link okhttp3.OkHttpClient}) than the
 * other parameters needed for construction.
 */
final class AsyncQosIoExceptionHandlerFactory implements QosIoExceptionHandlerProvider {
    private final ScheduledExecutorService scheduledExecutorService;
    private final ExecutorService executorService;
    private final Supplier<BackoffStrategy> backoffStrategy;
    private final MultiServerRequestCreator requestCreator;

    @VisibleForTesting
    AsyncQosIoExceptionHandlerFactory(
            ScheduledExecutorService scheduledExecutorService,
            ExecutorService executorService,
            Supplier<BackoffStrategy> backoffStrategy,
            MultiServerRequestCreator requestCreator) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.executorService = executorService;
        this.backoffStrategy = backoffStrategy;
        this.requestCreator = requestCreator;
    }

    AsyncQosIoExceptionHandlerFactory(
            ScheduledExecutorService scheduledExecutorService,
            ExecutorService executorService,
            ClientConfiguration config) {
        this(scheduledExecutorService,
                executorService,
                () -> new ExponentialBackoff(config.maxNumRetries(), config.backoffSlotSize(), new Random()),
                new MultiServerRequestCreator(UrlSelectorImpl.create(config.uris(), true)));
    }

    @Override
    public QosIoExceptionHandler createHandler(Call.Factory callFactory) {
        return new AsyncQosIoExceptionHandler(
                scheduledExecutorService, executorService, backoffStrategy.get(), requestCreator, callFactory);
    }
}
