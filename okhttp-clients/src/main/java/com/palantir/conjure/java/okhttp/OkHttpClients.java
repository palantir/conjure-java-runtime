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
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.service.UserAgent.Agent;
import com.palantir.conjure.java.api.config.service.UserAgents;
import com.palantir.conjure.java.client.config.CipherSuites;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tracing.Tracers;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
    private static final boolean RANDOMIZE = true;
    private static final boolean RESHUFFLE = true;

    @VisibleForTesting
    static final int NUM_SCHEDULING_THREADS = 5;

    private static final ThreadFactory executionThreads = new ThreadFactoryBuilder()
            .setUncaughtExceptionHandler((thread, uncaughtException) -> log.error(
                    "An exception was uncaught in an execution thread. "
                            + "This likely left a thread blocked, and is as such a serious bug "
                            + "which requires debugging.",
                    uncaughtException))
            .setNameFormat("remoting-okhttp-dispatcher-%d")
            // This diverges from the OkHttp default value, allowing the JVM to cleanly exit
            // while idle dispatcher threads are still alive.
            .setDaemon(true)
            .build();
    /**
     * The {@link ExecutorService} used for the {@link Dispatcher}s of all OkHttp clients created through this class.
     * Similar to OkHttp's default, but with two modifications:
     * <ol>
     *     <li>A logging uncaught exception handler</li>
     *     <li>
     *         Daemon threads: active request will not block JVM shutdown <b>unless</b> another non-daemon
     *         thread blocks waiting for the result. Most of our usage falls into this category. This allows
     *         JVM shutdown to occur cleanly without waiting a full minute after the last request completes.
     *     </li>
     * </ol>
     */
    private static final ExecutorService executionExecutor =
            Executors.newCachedThreadPool(executionThreads);

    /** Shared dispatcher with static executor service. */
    private static final Dispatcher dispatcher;

    /** Shared connection pool. */
    private static final ConnectionPool connectionPool = new ConnectionPool(100, 10, TimeUnit.MINUTES);

    private static DispatcherMetricSet dispatcherMetricSet;

    static {
        dispatcher = new Dispatcher(executionExecutor);
        // Restricting concurrency is done elsewhere in ConcurrencyLimiters.
        dispatcher.setMaxRequests(Integer.MAX_VALUE);
        // Must be less than maxRequests so a single slow host does not block all requests
        dispatcher.setMaxRequestsPerHost(256);

        dispatcherMetricSet = new DispatcherMetricSet(dispatcher, connectionPool);
    }

    /**
     * The {@link ScheduledExecutorService} used for recovering leaked limits.
     */
    private static final Supplier<ScheduledExecutorService> limitReviver = Suppliers.memoize(() -> Tracers.wrap(
            Executors.newSingleThreadScheduledExecutor(
                    Util.threadFactory("conjure-java-runtime/leaked limit reviver", true))));

    /**
     * The {@link ScheduledExecutorService} used for scheduling call retries. This thread pool is distinct from OkHttp's
     * internal thread pool and from the thread pool used by {@link #executionExecutor}.
     * <p>
     * Note: In contrast to the {@link java.util.concurrent.ThreadPoolExecutor} used by OkHttp's {@link
     * #executionExecutor}, {@code corePoolSize} must not be zero for a {@link ScheduledThreadPoolExecutor}, see its
     * Javadoc. Since this executor will never hit zero threads, it must use daemon threads.
     */
    private static final Supplier<ScheduledExecutorService> schedulingExecutor =
            Suppliers.memoize(() -> Tracers.wrap(Executors.newScheduledThreadPool(NUM_SCHEDULING_THREADS,
                    Util.threadFactory("conjure-java-runtime/OkHttp Scheduler", true))));

    private OkHttpClients() {}

    /**
     * Creates an OkHttp client from the given {@link ClientConfiguration}. Note that the configured {@link
     * ClientConfiguration#uris URIs} are initialized in random order.
     */
    public static OkHttpClient create(
            ClientConfiguration config,
            UserAgent userAgent,
            HostEventsSink hostEventsSink,
            Class<?> serviceClass) {
        boolean reshuffle =
                !config.nodeSelectionStrategy().equals(NodeSelectionStrategy.PIN_UNTIL_ERROR_WITHOUT_RESHUFFLE);
        return createInternal(config, userAgent, hostEventsSink, serviceClass, RANDOMIZE, reshuffle);
    }

    @VisibleForTesting
    static RemotingOkHttpClient withStableUris(
            ClientConfiguration config,
            UserAgent userAgent,
            HostEventsSink hostEventsSink,
            Class<?> serviceClass) {
        return createInternal(config, userAgent, hostEventsSink, serviceClass, !RANDOMIZE, !RESHUFFLE);
    }

    private static RemotingOkHttpClient createInternal(
            ClientConfiguration config,
            UserAgent userAgent,
            HostEventsSink hostEventsSink,
            Class<?> serviceClass,
            boolean randomizeUrlOrder,
            boolean reshuffle) {
        boolean enableClientQoS = shouldEnableQos(config.clientQoS());
        ConcurrencyLimiters concurrencyLimiters = new ConcurrencyLimiters(
                limitReviver.get(),
                config.taggedMetricRegistry(),
                serviceClass,
                enableClientQoS);

        OkHttpClient.Builder client = new OkHttpClient.Builder();
        client.addInterceptor(CatchThrowableInterceptor.INSTANCE);
        client.addInterceptor(SpanTerminatingInterceptor.INSTANCE);

        // Routing
        UrlSelectorImpl urlSelector = UrlSelectorImpl.createWithFailedUrlCooldown(
                randomizeUrlOrder ? UrlSelectorImpl.shuffle(config.uris()) : config.uris(),
                reshuffle,
                config.failedUrlCooldown(),
                Clock.systemUTC());
        if (config.meshProxy().isPresent()) {
            // TODO(rfink): Should this go into the call itself?
            client.addInterceptor(new MeshProxyInterceptor(config.meshProxy().get()));
        }
        client.followRedirects(false); // We implement our own redirect logic.

        // SSL
        client.sslSocketFactory(config.sslSocketFactory(), config.trustManager());
        if (config.fallbackToCommonNameVerification()) {
            client.hostnameVerifier(Okhttp39HostnameVerifier.INSTANCE);
        }

        // Intercept calls to augment request meta data
        if (enableClientQoS) {
            client.addInterceptor(new ConcurrencyLimitingInterceptor());
        }
        client.addInterceptor(InstrumentedInterceptor.create(
                config.taggedMetricRegistry(),
                hostEventsSink,
                serviceClass));
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
            client.proxyAuthenticator((route, response) -> response.request()
                    .newBuilder()
                    .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                    .build());
        }

        // cipher setup
        client.connectionSpecs(createConnectionSpecs(config.enableGcmCipherSuites()));

        // increase default connection pool from 5 @ 5 minutes to 100 @ 10 minutes
        client.connectionPool(connectionPool);

        client.dispatcher(dispatcher);

        // global metrics (addMetrics is idempotent, so this works even when multiple clients are created)
        config.taggedMetricRegistry()
                .addMetrics(
                        "from",
                        DispatcherMetricSet.class.getSimpleName(),
                        dispatcherMetricSet);

        return new RemotingOkHttpClient(
                client.build(),
                () -> new ExponentialBackoff(config.maxNumRetries(), config.backoffSlotSize()),
                config.nodeSelectionStrategy(),
                urlSelector,
                schedulingExecutor.get(),
                executionExecutor,
                concurrencyLimiters,
                config.serverQoS(),
                config.retryOnTimeout(),
                config.retryOnSocketException());
    }

    private static boolean shouldEnableQos(ClientConfiguration.ClientQoS clientQoS) {
        switch (clientQoS) {
            case ENABLED:
                return true;
            case DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS:
                return false;
        }

        throw new SafeIllegalStateException("Encountered unknown client QoS configuration",
                SafeArg.of("ClientQoS", clientQoS));
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
