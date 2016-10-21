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

import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link AsyncSpanObserver asynchronous SpanObserver} that logs every span to a configurable SLF4J {@link Logger}
 * with log-level {@link Logger#info INFO}. Logging is performed asynchronously on a given executor service.
 */
public final class AsyncSlf4jSpanObserver extends AsyncSpanObserver {

    private final Logger logger;

    private AsyncSlf4jSpanObserver(Logger logger, ExecutorService executorService) {
        super(executorService);
        this.logger = logger;
    }

    public static AsyncSlf4jSpanObserver of(ExecutorService executorService) {
        return new AsyncSlf4jSpanObserver(LoggerFactory.getLogger(AsyncSlf4jSpanObserver.class), executorService);
    }

    public static AsyncSlf4jSpanObserver of(Logger logger, ExecutorService executorService) {
        return new AsyncSlf4jSpanObserver(logger, executorService);
    }

    @Override
    public void doConsume(Span span) {
        if (logger.isTraceEnabled()) {
            logger.info("{}", span);
        }
    }
}
