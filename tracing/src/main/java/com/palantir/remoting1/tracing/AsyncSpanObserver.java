/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting1.tracing;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SpanObserver} whose observations are executed on a supplied {@link ExecutorService}.
 */
public final class AsyncSpanObserver implements SpanObserver {

    private static final Logger log = LoggerFactory.getLogger(AsyncSpanObserver.class);
    private static final int DEFAULT_MAX_INFLIGHTS = 10_000;

    private final ListeningExecutorService executorService;
    private final SpanObserver observer;

    private final AtomicInteger numInflights = new AtomicInteger(0); // number of non-completed observations
    private final int maxInflights;

    private AsyncSpanObserver(ExecutorService executorService, SpanObserver observer, int maxInflights) {
        this.executorService = MoreExecutors.listeningDecorator(executorService);
        this.observer = observer;
        this.maxInflights = maxInflights;
    }

    /**
     * Like {@link #create(ExecutorService, SpanObserver, int)}, but with at most {@link #DEFAULT_MAX_INFLIGHTS}
     * concurrent observations.
     */
    public static AsyncSpanObserver create(ExecutorService executorService, SpanObserver observer) {
        return new AsyncSpanObserver(executorService, observer, DEFAULT_MAX_INFLIGHTS);
    }

    /**
     * Creates a new {@link AsyncSpanObserver} that handles at most {@code maxInflights} number of concurrent
     * observations. Any additional concurrent observation is discarded and logged.
     */
    public static AsyncSpanObserver create(
            ExecutorService executorService, SpanObserver observer, int maxInflights) {
        return new AsyncSpanObserver(executorService, observer, maxInflights);
    }

    @Override
    public void consume(final Span span) {
        if (numInflights.incrementAndGet() <= maxInflights) {
            ListenableFuture<Span> future = executorService.submit(new Callable<Span>() {
                @Override
                public Span call() throws Exception {
                    observer.consume(span);
                    return span;
                }
            });

            Futures.addCallback(future, new FutureCallback<Span>() {
                @Override
                public void onSuccess(Span result) {
                    numInflights.decrementAndGet();
                }

                @Override
                public void onFailure(Throwable error) {
                    log.trace("Failed to notify observer", error);
                    numInflights.decrementAndGet();
                }
            });
        } else {
            log.trace("Failed to notify span observer since the maximum number of allowed concurrent observations was "
                    + "exceeded: {}", maxInflights);
            numInflights.decrementAndGet();
        }
    }
}
