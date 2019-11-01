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

package com.palantir.conjure.java.okhttp;

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.tracing.DetachedSpan;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link OkHttpClient} that executes {@link okhttp3.Call}s as {@link RemotingOkHttpCall}s in order to retry a class
 * of retryable error states.
 */
final class RemotingOkHttpClient extends ForwardingOkHttpClient {
    private static final Logger log = LoggerFactory.getLogger(RemotingOkHttpClient.class);

    private static final int MAX_NUM_RELOCATIONS = 20;

    private final Supplier<BackoffStrategy> backoffStrategyFactory;
    private final NodeSelectionStrategy nodeSelectionStrategy;
    private final UrlSelector urls;
    private final ScheduledExecutorService schedulingExecutor;
    private final ExecutorService executionExecutor;
    private final ConcurrencyLimiters concurrencyLimiters;
    private final ClientConfiguration.ServerQoS serverQoS;
    private final ClientConfiguration.RetryOnTimeout retryOnTimeout;
    private final ClientConfiguration.RetryOnSocketException retryOnSocketException;

    RemotingOkHttpClient(
            OkHttpClient delegate,
            Supplier<BackoffStrategy> backoffStrategy,
            NodeSelectionStrategy nodeSelectionStrategy,
            UrlSelector urls,
            ScheduledExecutorService schedulingExecutor,
            ExecutorService executionExecutor,
            ConcurrencyLimiters concurrencyLimiters,
            ClientConfiguration.ServerQoS serverQoS,
            ClientConfiguration.RetryOnTimeout retryOnTimeout,
            ClientConfiguration.RetryOnSocketException retryOnSocketException) {
        super(delegate);
        this.backoffStrategyFactory = backoffStrategy;
        this.nodeSelectionStrategy = nodeSelectionStrategy;
        this.urls = urls;
        this.schedulingExecutor = schedulingExecutor;
        this.executionExecutor = executionExecutor;
        this.concurrencyLimiters = concurrencyLimiters;
        this.serverQoS = serverQoS;
        this.retryOnTimeout = retryOnTimeout;
        this.retryOnSocketException = retryOnSocketException;
    }

    @Override
    public RemotingOkHttpCall newCall(Request request) {
        return newCallWithMutableState(createNewRequest(request), backoffStrategyFactory.get(), MAX_NUM_RELOCATIONS);
    }

    @Override
    public Builder newBuilder() {
        log.warn(
                "Attempting to copy RemotingOkHttpClient. Some of the functionality like rate limiting and qos will "
                        + "not be available to the new client",
                new SafeRuntimeException("stacktrace"));
        return super.newBuilder();
    }

    RemotingOkHttpCall newCallWithMutableState(
            Request request, BackoffStrategy backoffStrategy, int maxNumRelocations) {
        return new RemotingOkHttpCall(
                getDelegate().newCall(request),
                backoffStrategy,
                urls,
                this,
                schedulingExecutor,
                executionExecutor,
                concurrencyLimiters.acquireLimiter(request),
                maxNumRelocations,
                serverQoS,
                retryOnTimeout,
                retryOnSocketException);
    }

    private Request createNewRequest(Request request) {
        String httpRemotingPath = request.header(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER);
        String spanName;
        if (httpRemotingPath != null) {
            spanName = "OkHttp: " + httpRemotingPath;
        } else {
            spanName = request.method();
        }
        DetachedSpan entireSpan = DetachedSpan.start(spanName);
        return request.newBuilder()
                .url(getNewRequestUrl(request.url()))
                .tag(ConcurrencyLimiterListener.class, ConcurrencyLimiterListener.create())
                .tag(Tags.EntireSpan.class, () -> entireSpan)
                .tag(Tags.AttemptSpan.class, Tags.AttemptSpan.createAttempt(entireSpan, 0))
                .tag(Tags.SettableDispatcherSpan.class, Tags.SettableDispatcherSpan.create())
                .tag(Tags.SettableWaitForBodySpan.class, Tags.SettableWaitForBodySpan.create())
                .build();
    }

    private HttpUrl getNewRequestUrl(HttpUrl requestUrl) {
        return redirectToNewRequest(requestUrl).orElse(requestUrl);
    }

    private Optional<HttpUrl> redirectToNewRequest(HttpUrl current) {
        switch (nodeSelectionStrategy) {
            case ROUND_ROBIN:
                return urls.redirectToNextRoundRobin(current);
            case PIN_UNTIL_ERROR:
            case PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE:
                return urls.redirectToCurrent(current);
        }

        throw new SafeIllegalStateException("Encountered unknown node selection strategy", SafeArg.of(
                "nodeSelectionStrategy", nodeSelectionStrategy));
    }
}
