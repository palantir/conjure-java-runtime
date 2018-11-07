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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.service.UserAgent.Agent;
import com.palantir.conjure.java.api.config.service.UserAgents;
import com.palantir.conjure.java.client.config.CipherSuites;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.tracing.Tracers;
import com.palantir.tracing.okhttp3.OkhttpTraceInterceptor;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import okhttp3.internal.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OkHttpClients {
    private static final Logger log = LoggerFactory.getLogger(OkHttpClients.class);

    @VisibleForTesting
    static final int NUM_SCHEDULING_THREADS = 5;

    private static final ThreadFactory executionThreads = new ThreadFactoryBuilder()
            .setUncaughtExceptionHandler((thread, uncaughtException) ->
                    log.error("An exception was uncaught in an execution thread. "
                                    + "This likely left a thread blocked, and is as such a serious bug "
                                    + "which requires debugging.",
                            uncaughtException))
            .setNameFormat("remoting-okhttp-dispatcher-%d")
            .build();
    /**
     * The {@link ExecutorService} used for the {@link Dispatcher}s of all OkHttp clients created through this class.
     * Same as OkHttp's default, but with a logging uncaught exception handler.
     */
    private static final ExecutorService executionExecutor =
            Tracers.wrap(Executors.newCachedThreadPool(executionThreads));

    /** Shared dispatcher with static executor service. */
    private static final Dispatcher dispatcher;

    /** Global {@link TaggedMetricRegistry} for per-client and dispatcher-wide metrics. */
    private static TaggedMetricRegistry registry = DefaultTaggedMetricRegistry.getDefault();

    /** Shared connection pool. */
    private static final ConnectionPool connectionPool = new ConnectionPool(100, 10, TimeUnit.MINUTES);

    static {
        dispatcher = new Dispatcher(executionExecutor);
        dispatcher.setMaxRequests(256);
        dispatcher.setMaxRequestsPerHost(256);
        // metrics
        registry.gauge(
                MetricName.builder().safeName("com.palantir.conjure.java.dispatcher.calls.queued").build(),
                dispatcher::queuedCallsCount);
        registry.gauge(
                MetricName.builder().safeName("com.palantir.conjure.java.dispatcher.calls.running").build(),
                dispatcher::runningCallsCount);
        registry.gauge(
                MetricName.builder().safeName("com.palantir.conjure.java.connection-pool.connections.total").build(),
                connectionPool::connectionCount);
        registry.gauge(
                MetricName.builder().safeName("com.palantir.conjure.java.connection-pool.connections.idle").build(),
                connectionPool::idleConnectionCount);
    }

    /**
     * The {@link ScheduledExecutorService} used for scheduling call retries. This thread pool is distinct from OkHttp's
     * internal thread pool and from the thread pool used by {@link #executionExecutor}.
     * <p>
     * Note: In contrast to the {@link java.util.concurrent.ThreadPoolExecutor} used by OkHttp's {@link
     * #executionExecutor}, {@code corePoolSize} must not be zero for a {@link ScheduledThreadPoolExecutor}, see its
     * Javadoc.
     */
    private static final ScheduledExecutorService schedulingExecutor = Tracers.wrap(Executors.newScheduledThreadPool(
            NUM_SCHEDULING_THREADS, Util.threadFactory("conjure-java-runtime/OkHttp Scheduler", false)));

    private OkHttpClients() {}

    /**
     * Creates an OkHttp client from the given {@link ClientConfiguration}. Note that the configured {@link
     * ClientConfiguration#uris URIs} are initialized in random order.
     */
    public static OkHttpClient create(
            ClientConfiguration config, UserAgent userAgent, HostEventsSink hostEventsSink, Class<?> serviceClass) {
        return createInternal(config, userAgent, hostEventsSink, serviceClass, true /* randomize URLs */);
    }

    @VisibleForTesting
    static RemotingOkHttpClient withStableUris(
            ClientConfiguration config, UserAgent userAgent, HostEventsSink hostEventsSink, Class<?> serviceClass) {
        return createInternal(config, userAgent, hostEventsSink, serviceClass, false);
    }

    private static RemotingOkHttpClient createInternal(
            ClientConfiguration config,
            UserAgent userAgent,
            HostEventsSink hostEventsSink,
            Class<?> serviceClass,
            boolean randomizeUrlOrder) {
        OkHttpClient.Builder client = new OkHttpClient.Builder();

        // Routing
        UrlSelectorImpl urlSelector = UrlSelectorImpl.createWithFailedUrlCooldown(config.uris(), randomizeUrlOrder,
                config.failedUrlCooldown());
        if (config.meshProxy().isPresent()) {
            // TODO(rfink): Should this go into the call itself?
            client.addInterceptor(new MeshProxyInterceptor(config.meshProxy().get()));
        } else {
            switch (config.nodeSelectionStrategy()) {
                case ROUND_ROBIN:
                    client.addInterceptor(RoundRobinUrlInterceptor.create(urlSelector));
                    break;
                case PIN_UNTIL_ERROR:
                    // Add CurrentUrlInterceptor: always selects the "current" URL, rather than the one specified in
                    // the request
                    client.addInterceptor(CurrentUrlInterceptor.create(urlSelector));
            }
        }
        client.followRedirects(false);  // We implement our own redirect logic.

        // SSL
        client.sslSocketFactory(config.sslSocketFactory(), config.trustManager());

        // Intercept calls to augment request meta data
        client.addInterceptor(new ConcurrencyLimitingInterceptor(registry, serviceClass));
        client.addInterceptor(InstrumentedInterceptor.create(registry, hostEventsSink, serviceClass));
        client.addInterceptor(OkhttpTraceInterceptor.INSTANCE);
        client.addInterceptor(UserAgentInterceptor.of(augmentUserAgent(userAgent, serviceClass)));

        // timeouts
        // Note that Feign overrides OkHttp timeouts with the timeouts given in FeignBuilder#Options if given, or
        // with its own default otherwise.
        client.connectTimeout(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
        client.readTimeout(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS);
        client.writeTimeout(config.writeTimeout().toMillis(), TimeUnit.MILLISECONDS);

        // proxy
        client.proxySelector(config.proxy());
        if (config.proxyCredentials().isPresent()) {
            BasicCredentials basicCreds = config.proxyCredentials().get();
            final String credentials = Credentials.basic(basicCreds.username(), basicCreds.password());
            client.proxyAuthenticator((route, response) -> response.request().newBuilder()
                    .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                    .build());
        }

        // cipher setup
        client.connectionSpecs(createConnectionSpecs(config.enableGcmCipherSuites()));

        // increase default connection pool from 5 @ 5 minutes to 100 @ 10 minutes
        client.connectionPool(connectionPool);

        client.dispatcher(dispatcher);

        return new RemotingOkHttpClient(
                client.build(),
                () -> new ExponentialBackoff(
                        config.maxNumRetries(), config.backoffSlotSize(), ThreadLocalRandom.current()),
                urlSelector,
                schedulingExecutor,
                executionExecutor);
    }

    /**
     * Adds informational {@link Agent}s to the given {@link UserAgent}, one for the conjure-java-runtime library and
     * one for the given service class. Version strings are extracted from the packages'
     * {@link Package#getImplementationVersion implementation version}, defaulting to 0.0.0 if no version can be found.
     */
    private static UserAgent augmentUserAgent(UserAgent agent, Class<?> serviceClass) {
        UserAgent augmentedAgent = agent;

        String maybeServiceVersion = serviceClass.getPackage().getImplementationVersion();
        augmentedAgent = augmentedAgent.addAgent(UserAgent.Agent.of(
                serviceClass.getSimpleName(),
                maybeServiceVersion != null ? maybeServiceVersion : "0.0.0"));

        String maybeRemotingVersion = OkHttpClients.class.getPackage().getImplementationVersion();
        augmentedAgent = augmentedAgent.addAgent(UserAgent.Agent.of(
                UserAgents.CONJURE_AGENT_NAME,
                maybeRemotingVersion != null ? maybeRemotingVersion : "0.0.0"));
        return augmentedAgent;
    }

    private static ImmutableList<ConnectionSpec> createConnectionSpecs(boolean enableGcmCipherSuites) {
        return ImmutableList.of(
                new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .tlsVersions(TlsVersion.TLS_1_2)
                        .cipherSuites(enableGcmCipherSuites
                                ? CipherSuites.allCipherSuites()
                                : CipherSuites.fastCipherSuites())
                        .build(),
                ConnectionSpec.CLEARTEXT);
    }

}
