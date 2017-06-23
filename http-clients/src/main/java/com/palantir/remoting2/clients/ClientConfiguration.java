/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.clients;

import com.palantir.remoting.api.config.service.BasicCredentials;
import com.palantir.remoting.api.config.service.ServiceConfiguration;
import java.net.ProxySelector;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.immutables.value.Value;

/**
 * A context-independent (i.e., does not depend on configuration files or on-disk entities like JKS
 * keystores) instantiation of a {@link ServiceConfiguration}.
 */
@Value.Immutable
@ImmutablesStyle
public interface ClientConfiguration {
    SSLSocketFactory sslSocketFactory();
    X509TrustManager trustManager();
    List<String> uris();
    Duration connectTimeout();
    Duration readTimeout();
    Duration writeTimeout();
    boolean enableGcmCipherSuites();
    ProxySelector proxy();
    Optional<BasicCredentials> proxyCredentials();
    int maxNumRetries();

    static Builder builder() {
        return new Builder();
    }

    class Builder extends ImmutableClientConfiguration.Builder {}
}
