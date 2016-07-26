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

package com.palantir.remoting.clients;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.palantir.config.service.Duration;
import com.palantir.config.service.ProxyConfiguration;
import com.palantir.config.service.ServiceConfiguration;
import com.palantir.remoting.ssl.SslSocketFactories;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.immutables.value.Value;

/** Implementation-independent configuration options for HTTP-based dynamic proxies. */
@Value.Immutable
@SuppressWarnings("checkstyle:designforextension")
public abstract class ClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.seconds(10);
    private static final Duration READ_TIMEOUT = Duration.minutes(10);
    private static final Duration WRITE_TIMEOUT = Duration.minutes(10);
    private static final int MAX_NUM_RETRIES = 1;

    @Value.Parameter
    public abstract Optional<SSLSocketFactory> sslSocketFactory();

    @Value.Parameter
    public abstract Optional<X509TrustManager> trustManager();

    @Value.Default
    public Duration connectTimeout() {
        return CONNECT_TIMEOUT;
    }

    @Value.Default
    public Duration readTimeout() {
        return READ_TIMEOUT;
    }

    @Value.Default
    public Duration writeTimeout() {
        return WRITE_TIMEOUT;
    }

    @Value.Parameter
    public abstract Optional<ProxyConfiguration> proxy();

    @Value.Default
    public Integer maxNumRetries() {
        return MAX_NUM_RETRIES;
    }

    @Value.Check
    public void check() {
        Preconditions.checkState(sslSocketFactory().isPresent() == trustManager().isPresent(),
                "Must set either both sslSocketFactory and TrustManager, or neither");
    }

    public static ClientConfig fromServiceConfig(ServiceConfiguration serviceConfig) {
        ClientConfig.Builder clientConfig = builder();

        // ssl
        if (serviceConfig.security().isPresent()) {
            clientConfig.sslSocketFactory(SslSocketFactories.createSslSocketFactory(serviceConfig.security().get()));
            clientConfig.trustManager(
                    (X509TrustManager) SslSocketFactories.createTrustManagers(serviceConfig.security().get())[0]);
        }

        // timeouts & proxy
        clientConfig.connectTimeout(serviceConfig.connectTimeout().or(CONNECT_TIMEOUT));
        clientConfig.readTimeout(serviceConfig.readTimeout().or(READ_TIMEOUT));
        clientConfig.writeTimeout(serviceConfig.writeTimeout().or(WRITE_TIMEOUT));
        clientConfig.proxy(serviceConfig.proxyConfiguration());

        return clientConfig.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends ImmutableClientConfig.Builder {}
}
