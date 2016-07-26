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

/** Implementation-independent configuration options for HTTP-based dynamic proxies. */
// TODO(rfink) Use immutables? How is this different from ServiceConfiguration? Is it just the SslConfiguration?
public final class ClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.seconds(10);
    private static final Duration READ_TIMEOUT = Duration.minutes(10);
    private static final Duration WRITE_TIMEOUT = Duration.minutes(10);
    private static final int MAX_NUM_RETRIES = 1;

    private Optional<SSLSocketFactory> thisSslSocketFactory = Optional.absent();
    private Optional<X509TrustManager> thisTrustManager = Optional.absent();
    private Optional<Duration> thisConnectTimeout = Optional.absent();
    private Optional<Duration> thisReadTimeout = Optional.absent();
    private Optional<Duration> thisWriteTimeout = Optional.absent();
    private Optional<ProxyConfiguration> thisProxyConfiguration = Optional.absent();
    private Optional<Integer> thisMaxNumRetries = Optional.absent();


    public Optional<SSLSocketFactory> getSslSocketFactory() {
        return thisSslSocketFactory;
    }

    public Optional<X509TrustManager> getX509TrustManager() {
        return thisTrustManager;
    }

    public Duration getConnectTimeout() {
        return thisConnectTimeout.or(CONNECT_TIMEOUT);
    }

    public Duration getReadTimeout() {
        return thisReadTimeout.or(READ_TIMEOUT);
    }

    public Duration getWriteTimeout() {
        return thisWriteTimeout.or(WRITE_TIMEOUT);
    }

    public int getMaxNumRetries() {
        return thisMaxNumRetries.or(MAX_NUM_RETRIES);
    }

    public Optional<ProxyConfiguration> getProxyConfiguration() {
        return thisProxyConfiguration;
    }

    public static ClientConfig empty() {
        return new ClientConfig();
    }

    public static ClientConfig fromServiceConfig(ServiceConfiguration serviceConfig) {
        ClientConfig clientConfig = new ClientConfig();

        // ssl
        if (serviceConfig.security().isPresent()) {
            clientConfig.ssl(SslSocketFactories.createSslSocketFactory(serviceConfig.security().get()),
                    (X509TrustManager) SslSocketFactories.createTrustManagers(serviceConfig.security().get())[0]);
        }

        // timeouts & proxy
        clientConfig.connectTimeout(serviceConfig.connectTimeout());
        clientConfig.readTimeout(serviceConfig.readTimeout());
        clientConfig.writeTimeout(serviceConfig.writeTimeout());
        clientConfig.proxy(serviceConfig.proxyConfiguration());

        return clientConfig;
    }

    public ClientConfig ssl(SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
        verifySslConfigurationUnset();
        thisSslSocketFactory = Optional.of(sslSocketFactory);
        thisTrustManager = Optional.of(trustManager);
        return this;
    }

    public ClientConfig connectTimeout(Optional<Duration> duration) {
        if (duration.isPresent()) {
            connectTimeout(duration.get());
        }
        return this;
    }

    public ClientConfig connectTimeout(Duration duration) {
        Preconditions.checkArgument(!thisConnectTimeout.isPresent(), "connectTimeout already set");
        thisConnectTimeout = Optional.of(duration);
        return this;
    }

    public ClientConfig readTimeout(Optional<Duration> duration) {
        if (duration.isPresent()) {
            readTimeout(duration.get());
        }
        return this;
    }

    public ClientConfig readTimeout(Duration duration) {
        Preconditions.checkArgument(!thisReadTimeout.isPresent(), "readTimeout already set");
        thisReadTimeout = Optional.of(duration);
        return this;
    }

    public ClientConfig writeTimeout(Optional<Duration> duration) {
        if (duration.isPresent()) {
            writeTimeout(duration.get());
        }
        return this;
    }

    public ClientConfig writeTimeout(Duration duration) {
        Preconditions.checkArgument(!thisWriteTimeout.isPresent(), "writeTimeout already set");
        thisWriteTimeout = Optional.of(duration);
        return this;
    }

    public ClientConfig maxNumRetries(int maxNumRetries) {
        Preconditions.checkArgument(!thisMaxNumRetries.isPresent(), "maxNumRetries already set");
        thisMaxNumRetries = Optional.of(maxNumRetries);
        return this;
    }

    public ClientConfig proxy(Optional<ProxyConfiguration> proxyConfiguration) {
        if (proxyConfiguration.isPresent()) {
            proxy(proxyConfiguration.get());
        }
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
