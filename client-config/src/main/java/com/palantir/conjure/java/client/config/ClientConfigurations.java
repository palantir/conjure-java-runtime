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

package com.palantir.conjure.java.client.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.palantir.conjure.java.api.config.service.ProxyConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.conjure.java.config.ssl.TrustContext;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/** Utilities for creating {@link ClientConfiguration} instances. */
public final class ClientConfigurations {

    private static final SafeLogger log = SafeLoggerFactory.get(ClientConfigurations.class);

    // Defaults for parameters that are optional in ServiceConfiguration.
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DEFAULT_WRITE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DEFAULT_BACKOFF_SLOT_SIZE = Duration.ofMillis(250);
    private static final Duration DEFAULT_FAILED_URL_COOLDOWN = Duration.ZERO;
    // GCM ciphers perform poorly using a java 1.8 runtime, but improve significantly in 9 and beyond.
    // See http://openjdk.java.net/jeps/246
    // Given the low volume of services still using java8, it's safer to avoid failing handshakes
    // at a performance cost.
    private static final boolean DEFAULT_ENABLE_GCM_CIPHERS = true;
    private static final boolean DEFAULT_FALLBACK_TO_COMMON_NAME_VERIFICATION = false;
    private static final NodeSelectionStrategy DEFAULT_NODE_SELECTION_STRATEGY = NodeSelectionStrategy.PIN_UNTIL_ERROR;
    private static final int DEFAULT_MAX_NUM_RETRIES = 4;
    private static final ClientConfiguration.ClientQoS CLIENT_QOS_DEFAULT = ClientConfiguration.ClientQoS.ENABLED;
    private static final ClientConfiguration.ServerQoS PROPAGATE_QOS_DEFAULT =
            ClientConfiguration.ServerQoS.AUTOMATIC_RETRY;
    private static final ClientConfiguration.RetryOnTimeout RETRY_ON_TIMEOUT_DEFAULT =
            ClientConfiguration.RetryOnTimeout.DISABLED;
    private static final ClientConfiguration.RetryOnSocketException RETRY_ON_SOCKET_EXCEPTION_DEFAULT =
            ClientConfiguration.RetryOnSocketException.ENABLED;
    private static final String ENV_HTTPS_PROXY = "https_proxy";

    private ClientConfigurations() {}

    /**
     * Creates a new {@link ClientConfiguration} instance from the given {@link ServiceConfiguration}, filling in
     * empty/absent configuration with the defaults specified as constants in this class.
     */
    public static ClientConfiguration of(ServiceConfiguration config) {
        return ClientConfigurations.of(config, SslSocketFactories::createTrustContext);
    }

    /**
     * Creates a new {@link ClientConfiguration} instance from the given {@link ServiceConfiguration} and
     * {@link TrustContextFactory}, filling in empty/absent configuration with the defaults specified as constants
     * in this class.
     */
    public static ClientConfiguration of(ServiceConfiguration config, TrustContextFactory factory) {
        TrustContext trustContext = factory.create(config.security());
        return ClientConfiguration.builder()
                .sslSocketFactory(trustContext.sslSocketFactory())
                .trustManager(trustContext.x509TrustManager())
                .uris(config.uris())
                .connectTimeout(config.connectTimeout().orElse(DEFAULT_CONNECT_TIMEOUT))
                .readTimeout(config.readTimeout().orElse(DEFAULT_READ_TIMEOUT))
                .writeTimeout(config.writeTimeout().orElse(DEFAULT_WRITE_TIMEOUT))
                .enableGcmCipherSuites(config.enableGcmCipherSuites().orElse(DEFAULT_ENABLE_GCM_CIPHERS))
                .fallbackToCommonNameVerification(
                        config.fallbackToCommonNameVerification().orElse(DEFAULT_FALLBACK_TO_COMMON_NAME_VERIFICATION))
                .proxy(config.proxy()
                        .map(ClientConfigurations::createProxySelector)
                        .orElseGet(ProxySelector::getDefault))
                .proxyCredentials(config.proxy().flatMap(ProxyConfiguration::credentials))
                .meshProxy(meshProxy(config.proxy()))
                .maxNumRetries(config.maxNumRetries().orElse(DEFAULT_MAX_NUM_RETRIES))
                .nodeSelectionStrategy(DEFAULT_NODE_SELECTION_STRATEGY)
                .failedUrlCooldown(DEFAULT_FAILED_URL_COOLDOWN)
                .backoffSlotSize(config.backoffSlotSize().orElse(DEFAULT_BACKOFF_SLOT_SIZE))
                .clientQoS(CLIENT_QOS_DEFAULT)
                .serverQoS(PROPAGATE_QOS_DEFAULT)
                .retryOnTimeout(RETRY_ON_TIMEOUT_DEFAULT)
                .retryOnSocketException(RETRY_ON_SOCKET_EXCEPTION_DEFAULT)
                .taggedMetricRegistry(SharedTaggedMetricRegistries.getSingleton())
                .build();
    }

    /**
     * Creates a new {@link ClientConfiguration} instance from the given SSL configuration and URIs, filling in all
     * other configuration with the defaults specified as constants in this class.
     */
    public static ClientConfiguration of(
            List<String> uris, SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
        return of(uris, sslSocketFactory, trustManager, Optional.empty());
    }

    /**
     * Creates a new {@link ClientConfiguration} instance from the given SSL configuration, URIs, and user agent,
     * filling in all other configuration with the defaults specified as constants in this class.
     */
    public static ClientConfiguration of(
            List<String> uris, SSLSocketFactory sslSocketFactory, X509TrustManager trustManager, UserAgent userAgent) {
        return of(uris, sslSocketFactory, trustManager, Optional.of(userAgent));
    }

    private static ClientConfiguration of(
            List<String> uris,
            SSLSocketFactory sslSocketFactory,
            X509TrustManager trustManager,
            Optional<UserAgent> userAgent) {
        return ClientConfiguration.builder()
                .sslSocketFactory(sslSocketFactory)
                .trustManager(trustManager)
                .uris(uris)
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .readTimeout(DEFAULT_READ_TIMEOUT)
                .writeTimeout(DEFAULT_WRITE_TIMEOUT)
                .enableGcmCipherSuites(DEFAULT_ENABLE_GCM_CIPHERS)
                .fallbackToCommonNameVerification(DEFAULT_FALLBACK_TO_COMMON_NAME_VERIFICATION)
                .proxy(ProxySelector.getDefault())
                .proxyCredentials(Optional.empty())
                .maxNumRetries(DEFAULT_MAX_NUM_RETRIES)
                .backoffSlotSize(DEFAULT_BACKOFF_SLOT_SIZE)
                .nodeSelectionStrategy(DEFAULT_NODE_SELECTION_STRATEGY)
                .failedUrlCooldown(DEFAULT_FAILED_URL_COOLDOWN)
                .clientQoS(CLIENT_QOS_DEFAULT)
                .serverQoS(PROPAGATE_QOS_DEFAULT)
                .retryOnTimeout(RETRY_ON_TIMEOUT_DEFAULT)
                .retryOnSocketException(RETRY_ON_SOCKET_EXCEPTION_DEFAULT)
                .taggedMetricRegistry(SharedTaggedMetricRegistries.getSingleton())
                .userAgent(userAgent)
                .build();
    }

    public static ProxySelector createProxySelector(ProxyConfiguration proxyConfig) {
        switch (proxyConfig.type()) {
            case DIRECT:
                return fixedProxySelectorFor(Proxy.NO_PROXY);
            case FROM_ENVIRONMENT:
                String defaultEnvProxy = System.getenv(ENV_HTTPS_PROXY);
                if (defaultEnvProxy == null) {
                    log.info("Proxy environment variable not set, using no proxy");
                    return fixedProxySelectorFor(Proxy.NO_PROXY);
                }
                log.info("Using proxy from environment variable", UnsafeArg.of("proxy", defaultEnvProxy));
                InetSocketAddress address = createInetSocketAddress(defaultEnvProxy);
                return fixedProxySelectorFor(new Proxy(Proxy.Type.HTTP, address));
            case HTTP:
                HostAndPort hostAndPort = HostAndPort.fromString(proxyConfig
                        .hostAndPort()
                        .orElseThrow(() -> new SafeIllegalArgumentException(
                                "Expected to find proxy hostAndPort configuration for HTTP proxy")));
                InetSocketAddress addr =
                        // Proxy address must not be resolved, otherwise DNS changes while the application
                        // is running are ignored by the application.
                        InetSocketAddress.createUnresolved(hostAndPort.getHost(), hostAndPort.getPort());
                return fixedProxySelectorFor(new Proxy(Proxy.Type.HTTP, addr));
            case MESH:
                return ProxySelector.getDefault(); // MESH proxy is not a Java proxy
            case SOCKS:
                HostAndPort socksHostAndPort = HostAndPort.fromString(proxyConfig
                        .hostAndPort()
                        .orElseThrow(() -> new SafeIllegalArgumentException(
                                "Expected to find proxy hostAndPort configuration for SOCKS proxy")));
                InetSocketAddress socksAddress =
                        // Proxy address must not be resolved, otherwise DNS changes while the application
                        // is running are ignored by the application.
                        InetSocketAddress.createUnresolved(socksHostAndPort.getHost(), socksHostAndPort.getPort());
                return fixedProxySelectorFor(new Proxy(Proxy.Type.SOCKS, socksAddress));
            default:
                // fall through
        }

        throw new IllegalStateException("Failed to create ProxySelector for proxy configuration: " + proxyConfig);
    }

    @VisibleForTesting
    static InetSocketAddress createInetSocketAddress(String uriString) {
        URI uri = URI.create(uriString);
        return InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
    }

    private static Optional<HostAndPort> meshProxy(Optional<ProxyConfiguration> proxy) {
        if (proxy.isPresent() && proxy.get().type() == ProxyConfiguration.Type.MESH) {
            return Optional.of(HostAndPort.fromString(proxy.get().hostAndPort().get()));
        } else {
            return Optional.empty();
        }
    }

    private static ProxySelector fixedProxySelectorFor(Proxy proxy) {
        return new FixedProxySelector(proxy);
    }

    private static final class FixedProxySelector extends ProxySelector {
        private final Proxy proxy;

        FixedProxySelector(Proxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public List<Proxy> select(URI _uri) {
            return ImmutableList.of(proxy);
        }

        @Override
        public void connectFailed(URI _uri, SocketAddress _sa, IOException _ioe) {}

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || FixedProxySelector.class != other.getClass()) {
                return false;
            }
            FixedProxySelector that = (FixedProxySelector) other;
            return proxy.equals(that.proxy);
        }

        @Override
        public int hashCode() {
            return proxy.hashCode();
        }

        @Override
        public String toString() {
            return "FixedProxySelector{proxy=" + proxy + '}';
        }
    }

    /**
     * A factory for {@link TrustContext} which allows to define how one will be created,
     * based on implementer defined security configurations. See {@link SslConfiguration}.
     */
    public interface TrustContextFactory {
        TrustContext create(SslConfiguration configuration);
    }
}
