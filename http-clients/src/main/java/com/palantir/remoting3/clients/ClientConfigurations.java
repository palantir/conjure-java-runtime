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

package com.palantir.remoting3.clients;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.palantir.remoting.api.config.service.ProxyConfiguration;
import com.palantir.remoting.api.config.service.ServiceConfiguration;
import com.palantir.remoting3.config.ssl.SslSocketFactories;
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

    // Defaults for parameters that are optional in ServiceConfiguration.
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration DEFAULT_WRITE_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration DEFAULT_BACKOFF_SLOT_SIZE = Duration.ofMillis(250);
    private static final boolean DEFAULT_ENABLE_GCM_CIPHERS = false;

    private ClientConfigurations() {}

    /**
     * Creates a new {@link ClientConfiguration} instance from the given {@link ServiceConfiguration}, filling in
     * empty/absent configuration with the defaults specified as constants in this class.
     */
    public static ClientConfiguration of(ServiceConfiguration config) {
        return ClientConfiguration.builder()
                .sslSocketFactory(SslSocketFactories.createSslSocketFactory(config.security()))
                .trustManager(SslSocketFactories.createX509TrustManager(config.security()))
                .uris(config.uris())
                .connectTimeout(config.connectTimeout().orElse(DEFAULT_CONNECT_TIMEOUT))
                .readTimeout(config.readTimeout().orElse(DEFAULT_READ_TIMEOUT))
                .writeTimeout(config.writeTimeout().orElse(DEFAULT_WRITE_TIMEOUT))
                .enableGcmCipherSuites(config.enableGcmCipherSuites().orElse(DEFAULT_ENABLE_GCM_CIPHERS))
                .proxy(config.proxy().map(ClientConfigurations::createProxySelector).orElse(ProxySelector.getDefault()))
                .proxyCredentials(config.proxy().flatMap(ProxyConfiguration::credentials))
                .meshProxy(meshProxy(config.proxy()))
                .maxNumRetries(config.maxNumRetries().orElse(config.uris().size()))
                .backoffSlotSize(config.backoffSlotSize().orElse(DEFAULT_BACKOFF_SLOT_SIZE))
                .build();
    }

    /**
     * Creates a new {@link ClientConfiguration} instance from the given SSL configuration and URIs, filling in all
     * other configuration with the defaults specified as constants in this class.
     */
    public static ClientConfiguration of(
            List<String> uris, SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
        return ClientConfiguration.builder()
                .sslSocketFactory(sslSocketFactory)
                .trustManager(trustManager)
                .uris(uris)
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .readTimeout(DEFAULT_READ_TIMEOUT)
                .writeTimeout(DEFAULT_WRITE_TIMEOUT)
                .enableGcmCipherSuites(DEFAULT_ENABLE_GCM_CIPHERS)
                .proxy(ProxySelector.getDefault())
                .proxyCredentials(Optional.empty())
                .maxNumRetries(uris.size())
                .backoffSlotSize(DEFAULT_BACKOFF_SLOT_SIZE)
                .build();
    }

    public static ProxySelector createProxySelector(ProxyConfiguration proxyConfig) {
        switch (proxyConfig.type()) {
            case DIRECT:
                return fixedProxySelectorFor(Proxy.NO_PROXY);
            case HTTP:
                HostAndPort hostAndPort = HostAndPort.fromString(proxyConfig.hostAndPort()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Expected to find proxy hostAndPort configuration for HTTP proxy")));
                InetSocketAddress addr = new InetSocketAddress(hostAndPort.getHost(), hostAndPort.getPort());
                return fixedProxySelectorFor(new Proxy(Proxy.Type.HTTP, addr));
            case MESH:
                return ProxySelector.getDefault(); // MESH proxy is not a Java proxy
            default:
                // fall through
        }

        throw new IllegalStateException("Failed to create ProxySelector for proxy configuration: " + proxyConfig);
    }

    private static Optional<HostAndPort> meshProxy(Optional<ProxyConfiguration> proxy) {
        if (proxy.isPresent() && proxy.get().type() == ProxyConfiguration.Type.MESH) {
            return Optional.of(HostAndPort.fromString(proxy.get().hostAndPort().get()));
        } else {
            return Optional.empty();
        }
    }

    private static ProxySelector fixedProxySelectorFor(Proxy proxy) {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return ImmutableList.of(proxy);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}
        };
    }
}
