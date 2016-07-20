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
import com.palantir.config.service.ProxyConfiguration;
import com.palantir.config.service.ServiceConfiguration;
import com.palantir.remoting.ssl.SslConfiguration;
import com.palantir.remoting.ssl.SslSocketFactories;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.joda.time.Duration;

// TODO(rfink) Is there anything JaxRs-specific in here? Can we reuse the same configuration for Retrofit2 clients?
public final class ClientConfig {
    private Optional<SSLSocketFactory> thisSslSocketFactory = Optional.absent();
    private Optional<X509TrustManager> thisTrustManager = Optional.absent();
    private Optional<Duration> thisConnectTimeout = Optional.absent();
    private Optional<Duration> thisReadTimeout = Optional.absent();
    private Optional<ProxyConfiguration> thisProxyConfiguration = Optional.absent();

    public Optional<SSLSocketFactory> getSslSocketFactory() {
        return thisSslSocketFactory;
    }

    public Optional<Duration> getConnectTimeout() {
        return thisConnectTimeout;
    }

    public Optional<Duration> getReadTimeout() {
        return thisReadTimeout;
    }

    public Optional<ProxyConfiguration> getProxyConfiguration() {
        return thisProxyConfiguration;
    }

    public static ClientConfig empty() {
        return new ClientConfig();
    }

    public static ClientConfig fromServiceConfig(ServiceConfiguration serviceConfig) {
        ClientConfig jaxRsConfig = new ClientConfig();

        // ssl
        jaxRsConfig.ssl(serviceConfig.security());

        // timeouts
        // TODO(rfink) Is there a better API for this?
        if (serviceConfig.connectTimeout().isPresent()) {
            jaxRsConfig.connectTimeout(serviceConfig.connectTimeout().get().toMilliseconds(), TimeUnit.MILLISECONDS);

        }
        if (serviceConfig.readTimeout().isPresent()) {
            jaxRsConfig.readTimeout(serviceConfig.readTimeout().get().toMilliseconds(), TimeUnit.MILLISECONDS);
        }
        if (serviceConfig.proxyConfiguration().isPresent()) {
            jaxRsConfig.proxy(serviceConfig.proxyConfiguration().get());
        }

        return jaxRsConfig;
    }

    public ClientConfig ssl(Optional<SslConfiguration> config) {
        if (config.isPresent()) {
            ssl(SslSocketFactories.createSslSocketFactory(config.get()),
                    (X509TrustManager) SslSocketFactories.createTrustManagers(config.get())[0]);
        }
        return this;
    }

    public ClientConfig ssl(SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
        verifySslConfigurationUnset();
        thisSslSocketFactory = Optional.of(sslSocketFactory);
        thisTrustManager = Optional.of(trustManager);
        return this;
    }

    public ClientConfig connectTimeout(long connectTimeout, TimeUnit unit) {
        Preconditions.checkArgument(!thisConnectTimeout.isPresent(), "connectTimeout already set");
        thisConnectTimeout = Optional.of(Duration.millis(TimeUnit.MILLISECONDS.convert(connectTimeout, unit)));
        return this;
    }

    public ClientConfig readTimeout(long readTimeout, TimeUnit unit) {
        Preconditions.checkArgument(!thisReadTimeout.isPresent(), "readTimeout already set");
        thisReadTimeout = Optional.of(Duration.millis(TimeUnit.MILLISECONDS.convert(readTimeout, unit)));
        return this;
    }

    public ClientConfig proxy(ProxyConfiguration proxyConfiguration) {
        Preconditions.checkArgument(!thisProxyConfiguration.isPresent(), "proxy already set");
        thisProxyConfiguration = Optional.of(proxyConfiguration);
        return this;
    }

    private void verifySslConfigurationUnset() {
        Preconditions.checkArgument(!thisSslSocketFactory.isPresent(), "sslSocketFactory already set");
        Preconditions.checkArgument(!thisTrustManager.isPresent(), "trustManager already set");
    }
}
