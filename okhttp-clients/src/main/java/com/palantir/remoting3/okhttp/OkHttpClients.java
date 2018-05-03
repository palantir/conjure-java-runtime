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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting.api.config.service.BasicCredentials;
import com.palantir.remoting3.clients.CipherSuites;
import com.palantir.remoting3.clients.ClientConfiguration;
import com.palantir.remoting3.clients.UserAgent;
import com.palantir.remoting3.clients.UserAgents;
import com.palantir.remoting3.tracing.Tracers;
import com.palantir.remoting3.tracing.okhttp3.OkhttpTraceInterceptor;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import okhttp3.Cache;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import okhttp3.internal.Util;

public final class OkHttpClients {

    @VisibleForTesting
    static final int NUM_SCHEDULING_THREADS = 5;

    /**
     * The {@link ExecutorService} used for the {@link Dispatcher}s of all OkHttp clients created through this class.
     */
    private static final ExecutorService executionExecutor = Tracers.wrap(new Dispatcher().executorService());

    /**
     * The {@link ScheduledExecutorService} used for scheduling call retries. This thread pool is distinct from OkHttp's
     * internal thread pool and from the thread pool used by {@link #executionExecutor}.
     * <p>
     * Note: In contrast to the {@link java.util.concurrent.ThreadPoolExecutor} used by OkHttp's {@link
     * #executionExecutor}, {@code corePoolSize} must not be zero for a {@link ScheduledThreadPoolExecutor}, see its
     * Javadoc.
     */
    private static final ScheduledExecutorService schedulingExecutor = Tracers.wrap(new ScheduledThreadPoolExecutor(
            NUM_SCHEDULING_THREADS, Util.threadFactory("http-remoting/OkHttp Scheduler", false)));

    /**
     * The per service and host metrics recorded for each HTTP call.
     */
    private static final HostMetricsRegistry hostMetrics = new HostMetricsRegistry();

    private OkHttpClients() {}

    /**
     * Creates an OkHttp client from the given {@link ClientConfiguration}. Note that the configured {@link
     * ClientConfiguration#uris URIs} are initialized in random order.
     */
    public static OkHttpClient create(ClientConfiguration config, UserAgent userAgent, Class<?> serviceClass) {
        return createInternal(config, userAgent, serviceClass, true /* randomize URLs */);
    }

    /**
     * Deprecated variant of {@link #create(ClientConfiguration, UserAgent, Class)}.
     *
     * @deprecated Use {@link #create(ClientConfiguration, UserAgent, Class)}
     */
    @Deprecated
    public static OkHttpClient create(ClientConfiguration config, String userAgent, Class<?> serviceClass) {
        return create(config, UserAgents.tryParse(userAgent), serviceClass);
    }

    /**
     * Return the per service and host metrics for all clients created by {@link OkHttpClients}.
     */
    public static Collection<HostMetrics> hostMetrics() {
        return hostMetrics.getMetrics();
    }

    @VisibleForTesting
    static RemotingOkHttpClient withStableUris(
            ClientConfiguration config, UserAgent userAgent, Class<?> serviceClass) {
        return createInternal(config, userAgent, serviceClass, false);
    }

    private static RemotingOkHttpClient createInternal(
            ClientConfiguration config,
            UserAgent userAgent,
            Class<?> serviceClass,
            boolean randomizeUrlOrder) {
        OkHttpClient.Builder client = new OkHttpClient.Builder();
        TaggedMetricRegistry registry = DefaultTaggedMetricRegistry.getDefault();

        // Routing
        UrlSelectorImpl urlSelector = UrlSelectorImpl.create(config.uris(), randomizeUrlOrder);
        if (config.meshProxy().isPresent()) {
            // TODO(rfink): Should this go into the call itself?
            client.addInterceptor(new MeshProxyInterceptor(config.meshProxy().get()));
        } else {
            // Add CurrentUrlInterceptor: always selects the "current" URL, rather than the one specified in the request
            client.addInterceptor(CurrentUrlInterceptor.create(urlSelector));
        }
        client.followRedirects(false);  // We implement our own redirect logic.

        // SSL
        client.sslSocketFactory(config.sslSocketFactory(), config.trustManager());

        // Intercept calls to augment request meta data
        client.addInterceptor(InstrumentedInterceptor.create(registry, hostMetrics, serviceClass));
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
        client.connectionPool(new ConnectionPool(100, 10, TimeUnit.MINUTES));

        // dispatcher with static executor service
        client.dispatcher(createDispatcher());

        // Cache
        config.cacheConfig().ifPresent(cacheConfig ->
                client.cache(new Cache(cacheConfig.directory(), cacheConfig.maxSizeMb() * 1024 * 1024)));

        return new RemotingOkHttpClient(
                client.build(),
                () -> new ExponentialBackoff(config.maxNumRetries(), config.backoffSlotSize(), new Random()),
                urlSelector,
                schedulingExecutor,
                executionExecutor,
                largestOf(config.connectTimeout(), config.readTimeout(), config.writeTimeout())
        );
    }

    /**
     * Treat {@link Duration#ZERO} as infinity to match okhttp3.
     */
    @VisibleForTesting
    static Duration largestOf(Duration... durations) {
        if (Arrays.stream(durations).anyMatch(Duration::isZero)) {
            return Duration.ZERO;
        }

        return Arrays.stream(durations)
                .max(Duration::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("largestOf must be called with at least one argument"));
    }

    /**
     * Adds informational {@link com.palantir.remoting3.clients.UserAgent.Agent}s to the given {@link UserAgent}, one
     * for the http-remoting library and one for the given service class. Version strings are extracted from the
     * packages' {@link Package#getImplementationVersion implementation version}, defaulting to 0.0.0 if no version can
     * be found.
     */
    private static UserAgent augmentUserAgent(UserAgent agent, Class<?> serviceClass) {
        UserAgent augmentedAgent = agent;

        String maybeServiceVersion = serviceClass.getPackage().getImplementationVersion();
        augmentedAgent = augmentedAgent.addAgent(UserAgent.Agent.of(
                serviceClass.getSimpleName(),
                maybeServiceVersion != null ? maybeServiceVersion : "0.0.0"));

        String maybeRemotingVersion = OkHttpClients.class.getPackage().getImplementationVersion();
        augmentedAgent = augmentedAgent.addAgent(UserAgent.Agent.of(
                UserAgents.REMOTING_AGENT_NAME,
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

    private static Dispatcher createDispatcher() {
        Dispatcher dispatcher = new Dispatcher(executionExecutor);
        dispatcher.setMaxRequests(256);
        dispatcher.setMaxRequestsPerHost(256);
        return dispatcher;
    }

}
