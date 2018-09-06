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

package com.palantir.remoting3.tracing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Lists;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.remoting.api.tracing.Span;
import com.palantir.remoting3.ext.jackson.ObjectMappers;
import com.palantir.remoting.api.tracing.SpanObserver;
import com.palantir.tracing.AsyncSpanObserver;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link AsyncSpanObserver asynchronous SpanObserver} that logs every span to a configurable SLF4J {@link Logger}
 * with log-level {@link Logger#info INFO}. Logging is performed asynchronously on a given executor service.
 */
public final class AsyncSlf4jSpanObserver implements SpanObserver {

    private final com.palantir.tracing.AsyncSlf4jSpanObserver delegate;

    @Override
    public void consume(Span span) {
        this.delegate.doConsume(span.asConjure());
    }

    @Override
    public com.palantir.tracing.api.SpanObserver asConjure() {
        return this.delegate;
    }

    private AsyncSlf4jSpanObserver(String serviceName, InetAddress ip, Logger logger, ExecutorService executorService) {
        this.delegate = com.palantir.tracing.AsyncSlf4jSpanObserver.of(serviceName, ip, logger, executorService);
    }

    public static AsyncSlf4jSpanObserver of(String serviceName, ExecutorService executorService) {
        return new AsyncSlf4jSpanObserver(serviceName, InetAddressSupplier.INSTANCE.get(),
                LoggerFactory.getLogger(AsyncSlf4jSpanObserver.class), executorService);
    }

    public static AsyncSlf4jSpanObserver of(
            String serviceName, InetAddress ip, Logger logger, ExecutorService executorService) {
        return new AsyncSlf4jSpanObserver(serviceName, ip, logger, executorService);
    }
}
