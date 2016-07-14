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

package com.palantir.remoting.jaxrs;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.palantir.remoting.ssl.SslConfiguration;
import com.palantir.remoting.ssl.SslSocketFactories;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.joda.time.Duration;

abstract class ClientBuilder {
    private Optional<SSLSocketFactory> thisSslSocketFactory;
    private Optional<X509TrustManager> thisTrustManager;
    private Optional<Duration> thisConnectTimeout;
    private Optional<Duration> thisReadTimeout;

    /**
     * Creates and returns a {@link T T client} from the builder configuration and parameters. Subsequent invocations
     * usually return different object.
     */
    public abstract <T> T build(Class<T> serviceClass, String userAgent, List<String> uris);

    /** Compare {@link #build}. */
    public <T> T build(Class<T> serviceClass, String userAgent, String... uris) {
        return build(serviceClass, userAgent, Arrays.asList(uris));
    }

    public ClientBuilder ssl(Optional<SslConfiguration> config) {
        if (config.isPresent()) {
            verifySslConfigurationUnset();
            thisSslSocketFactory = Optional.of(SslSocketFactories.createSslSocketFactory(config.get()));
            thisTrustManager = Optional.of(
                    (X509TrustManager) SslSocketFactories.createTrustManagers(config.get())[0]);
        }
        return this;
    }

    public ClientBuilder ssl(SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
        verifySslConfigurationUnset();
        thisSslSocketFactory = Optional.of(sslSocketFactory);
        thisTrustManager = Optional.of(trustManager);
        return this;
    }

    private void verifySslConfigurationUnset() {
        Preconditions.checkArgument(!thisSslSocketFactory.isPresent(), "sslSocketFactory already set");
        Preconditions.checkArgument(!thisTrustManager.isPresent(), "trustManager already set");
    }

    public ClientBuilder connectTimeout(long connectTimeout, TimeUnit unit) {
        if (thisConnectTimeout.isPresent()) {
            Preconditions.checkArgument(!thisConnectTimeout.isPresent(), "connectTimeout already set");
        }
        thisConnectTimeout = Optional.of(Duration.millis(TimeUnit.MILLISECONDS.convert(connectTimeout, unit)));
        return this;
    }

    public ClientBuilder readTimeout(long readTimeout, TimeUnit unit) {
        if (thisReadTimeout.isPresent()) {
            Preconditions.checkArgument(!thisReadTimeout.isPresent(), "readTimeout already set");
        }
        thisReadTimeout = Optional.of(Duration.millis(TimeUnit.MILLISECONDS.convert(readTimeout, unit)));
        return this;
    }

    // TODO(rfink) Add proxy support.

    Optional<SSLSocketFactory> getThisSslSocketFactory() {
        return thisSslSocketFactory;
    }

    Optional<Duration> getThisConnectTimeout() {
        return thisConnectTimeout;
    }

    Optional<Duration> getThisReadTimeout() {
        return thisReadTimeout;
    }
}
