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

package com.palantir.remoting2.config.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.palantir.remoting2.config.ssl.SslConfiguration;
import com.palantir.tokens2.auth.BearerToken;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

@Immutable
@JsonDeserialize(builder = ServiceConfiguration.Builder.class)
@JsonSerialize(as = ImmutableServiceConfiguration.class)
@Style(visibility = Style.ImplementationVisibility.PACKAGE)
public abstract class ServiceConfiguration {

    /**
     * The API token to be used to interact with the service.
     */
    public abstract Optional<BearerToken> apiToken();

    /**
     * The SSL configuration needed to interact with the service.
     */
    public abstract Optional<SslConfiguration> security();

    /**
     * Connect timeout for requests.
     */
    public abstract Optional<Duration> connectTimeout();

    /**
     * Read timeout for requests.
     */
    public abstract Optional<Duration> readTimeout();

    /**
     * Write timeout for requests.
     */
    public abstract Optional<Duration> writeTimeout();

    /**
     * Enable slower, but more standard cipher suite support, defaults to false.
     */
    public abstract Optional<Boolean> enableGcmCipherSuites();

    /**
     * A list of service URIs.
     */
    public abstract List<String> uris();

    /**
     * Proxy configuration for connecting to the service. If absent, uses system proxy configuration.
     */
    public abstract Optional<ProxyConfiguration> proxyConfiguration();

    public static ServiceConfiguration of(String uri, Optional<SslConfiguration> sslConfig) {
        return ServiceConfiguration.builder()
                .addUris(uri)
                .security(sslConfig)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // TODO(jnewman): #317 - remove kebab-case methods when Jackson 2.7 is picked up
    public static final class Builder extends ImmutableServiceConfiguration.Builder {

        @JsonProperty("api-token")
        Builder apiTokenKebabCase(Optional<BearerToken> apiToken) {
            return apiToken(apiToken);
        }

        @JsonProperty("connect-timeout")
        Builder connectTimeoutKebabCase(Optional<Duration> connectTimeout) {
            return connectTimeout(connectTimeout);
        }

        @JsonProperty("read-timeout")
        Builder readTimeoutKebabCase(Optional<Duration> readTimeout) {
            return readTimeout(readTimeout);
        }

        @JsonProperty("write-timeout")
        Builder writeTimeoutKebabCase(Optional<Duration> writeTimeout) {
            return writeTimeout(writeTimeout);
        }

        @JsonProperty("proxy-configuration")
        Builder proxyConfigurationKebabCase(Optional<ProxyConfiguration> proxyConfiguration) {
            return proxyConfiguration(proxyConfiguration);
        }

        @JsonProperty("enable-gcm-cipher-suites")
        Builder enableGcmCipherSuitesKebabCase(Optional<Boolean> enableGcmCipherSuites) {
            return enableGcmCipherSuites(enableGcmCipherSuites);
        }
    }
}
