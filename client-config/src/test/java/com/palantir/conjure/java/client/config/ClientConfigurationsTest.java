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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.palantir.conjure.java.api.config.service.ProxyConfiguration;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.logsafe.testing.Assertions;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.io.IOException;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.Test;

public final class ClientConfigurationsTest {

    private static final ImmutableList<String> uris = ImmutableList.of("uri");

    @Test
    public void testFromServiceConfig_fillsInDefaults() {
        ServiceConfiguration serviceConfig = ServiceConfiguration.builder()
                .uris(uris)
                .security(SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks")))
                .build();
        ClientConfiguration actual = ClientConfigurations.of(serviceConfig);

        assertThat(actual.sslSocketFactory()).isNotNull();
        assertThat(actual.trustManager()).isNotNull();
        assertThat(actual.uris()).isEqualTo(uris);
        assertThat(actual.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(actual.readTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(actual.writeTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(actual.enableGcmCipherSuites()).isFalse();
        assertThat(actual.fallbackToCommonNameVerification()).isFalse();
        assertThat(actual.proxy().select(URI.create("https://foo"))).containsExactly(Proxy.NO_PROXY);
        assertThat(actual.taggedMetricRegistry()).isSameAs(DefaultTaggedMetricRegistry.getDefault());
    }

    @Test
    public void testFromParameters_fillsInDefaults() {
        SSLSocketFactory sslFactory = mock(SSLSocketFactory.class);
        X509TrustManager trustManager = mock(X509TrustManager.class);
        ClientConfiguration actual = ClientConfigurations.of(uris, sslFactory, trustManager);

        assertThat(actual.sslSocketFactory()).isEqualTo(sslFactory);
        assertThat(actual.trustManager()).isEqualTo(trustManager);
        assertThat(actual.uris()).isEqualTo(uris);
        assertThat(actual.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(actual.readTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(actual.writeTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(actual.enableGcmCipherSuites()).isFalse();
        assertThat(actual.fallbackToCommonNameVerification()).isFalse();
        assertThat(actual.proxy().select(URI.create("https://foo"))).containsExactly(Proxy.NO_PROXY);
    }

    @Test
    public void testTimeoutMustBeMilliseconds() {
        ServiceConfiguration serviceConfig = ServiceConfiguration.builder()
                .uris(uris)
                .security(SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks")))
                .connectTimeout(Duration.ofNanos(5))
                .build();
        Assertions
                .assertThatLoggableExceptionThrownBy(() -> ClientConfigurations.of(serviceConfig))
                .hasLogMessage("Timeouts with sub-millisecond precision are not supported");
    }

    @Test
    public void meshProxy_maxRetriesMustBe0() throws Exception {
        assertThatThrownBy(() -> ClientConfigurations.of(meshProxyServiceConfig(uris, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("If meshProxy is configured then maxNumRetries must be 0");

        ClientConfiguration validConfig = ClientConfigurations.of(meshProxyServiceConfig(uris, 0));
        assertThat(validConfig.meshProxy()).isEqualTo(Optional.of(HostAndPort.fromParts("localhost", 1234)));
        assertThat(validConfig.maxNumRetries()).isZero();
    }

    @Test
    public void meshProxy_exactlyOneUri() throws Exception {
        assertThatThrownBy(() -> ClientConfigurations.of(meshProxyServiceConfig(ImmutableList.of("uri1", "uri2"), 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("If meshProxy is configured then uris must contain exactly 1 URI");
    }

    @Test
    @SuppressWarnings("CheckReturnValue")
    public void roundRobin_noCooldown() throws Exception {
        ServiceConfiguration serviceConfig = ServiceConfiguration.builder()
                .uris(uris)
                .security(SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks")))
                .build();

        assertThatThrownBy(() -> ClientConfiguration.builder()
                .from(
                        ClientConfigurations.of(serviceConfig))
                .nodeSelectionStrategy(NodeSelectionStrategy.ROUND_ROBIN)
                .failedUrlCooldown(Duration.ofMillis(0))
                .build())
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("If nodeSelectionStrategy is ROUND_ROBIN then failedUrlCooldown must be positive");
    }

    @Test
    public void overriding_tagged_metric_registry_is_convenient() {
        ServiceConfiguration serviceConfig = ServiceConfiguration.builder()
                .uris(uris)
                .security(SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks")))
                .build();

        ClientConfiguration overridden = ClientConfiguration.builder()
                .from(ClientConfigurations.of(serviceConfig))
                .taggedMetricRegistry(new DefaultTaggedMetricRegistry())
                .build();

        assertThat(overridden.taggedMetricRegistry()).isNotSameAs(DefaultTaggedMetricRegistry.getDefault());
    }

    @Test
    public void sslSocketFactory_has_keepalives_enabled() throws IOException {
        ClientConfiguration config = ClientConfigurations.of(ServiceConfiguration.builder()
                .uris(uris)
                .security(SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks")))
                .build());

        try (Socket socket = config.sslSocketFactory().createSocket("google.com", 443)) {
            assertThat(socket.getKeepAlive()).describedAs("keepAlives enabled").isTrue();
        }
    }

    @Test
    public void sensible_equality_and_hashcode() {
        SslConfiguration sslConfiguration = SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks"));
        SSLSocketFactory socketFactory = SslSocketFactories.createSslSocketFactory(sslConfiguration);
        X509TrustManager x509TrustManager = SslSocketFactories.createX509TrustManager(sslConfiguration);

        ClientConfiguration instance1 = ClientConfigurations.of(uris, socketFactory, x509TrustManager);
        ClientConfiguration instance2 = ClientConfigurations.of(uris, socketFactory, x509TrustManager);

        assertThat(instance1).isEqualTo(instance2);
        assertThat(instance1).hasSameHashCodeAs(instance2);
    }

    private ServiceConfiguration meshProxyServiceConfig(List<String> theUris, int maxNumRetries) {
        return ServiceConfiguration.builder()
                .uris(theUris)
                .security(SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks")))
                .proxy(ProxyConfiguration.mesh("localhost:1234"))
                .maxNumRetries(maxNumRetries)
                .build();
    }

}
