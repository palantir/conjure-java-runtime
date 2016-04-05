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

package com.palantir.config.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.palantir.remoting.ssl.SslConfiguration;
import com.palantir.tokens.auth.BearerToken;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Lazy;
import org.immutables.value.Value.Style;

/**
 * Configuration class that contains a map of {@code serviceName}s and their respective {@link ServiceConfiguration}s
 * and provides utility methods for apps to build service clients.
 */
@Immutable
@JsonDeserialize(as = ImmutableServiceDiscoveryConfiguration.class)
@Style(get = "*", visibility = Style.ImplementationVisibility.PACKAGE)
public abstract class ServiceDiscoveryConfiguration {

    /**
     * Fallback API token to be used if the service specific API token is not defined in the
     * {@link ServiceConfiguration}.
     */
    @JsonProperty("apiToken")
    public abstract Optional<BearerToken> getDefaultApiToken();

    /**
     * Fallback SSL Configuration to be used if the service specific SSL configuration is not defined in the
     * {@link ServiceConfiguration}.
     */
    @JsonProperty("security")
    public abstract Optional<SslConfiguration> getDefaultSecurity();

    @JsonProperty("services")
    abstract Map<String, ServiceConfiguration> originalServices();

    /**
     * A map of {@code serviceName}s as the keys and their respective {@link ServiceConfiguration}s as the values.
     */
    @Lazy
    @SuppressWarnings("checkstyle:designforextension")
    public Map<String, ServiceConfiguration> getServices() {
        Map<String, ServiceConfiguration> intializedServices = new HashMap<String, ServiceConfiguration>();

        for (Map.Entry<String, ServiceConfiguration> entry : originalServices().entrySet()) {
            ServiceConfiguration serviceConfig = entry.getValue();
            Optional<BearerToken> apiToken = serviceConfig.apiToken().or(getDefaultApiToken());
            Optional<SslConfiguration> security = serviceConfig.security().or(getDefaultSecurity());
            intializedServices.put(entry.getKey(),
                    ImmutableServiceConfiguration.of(apiToken, security, serviceConfig.uris()));
        }

        return Collections.unmodifiableMap(intializedServices);
    }

    /**
     * Uses the provided service name as the key to retrieve the API token for the respective
     * {@link ServiceConfiguration}. If the API token is not defined in the {@link ServiceConfiguration}, the default
     * API token will be returned.
     *
     * @param serviceName
     *        the name of the service to be used to retrieve the respective {@link ServiceConfiguration}
     * @return the API token for a service based on the input service name
     */
    public final Optional<BearerToken> getApiToken(String serviceName) {
        ServiceConfiguration serviceConfig =
                Preconditions.checkNotNull(
                        getServices().get(serviceName), "Unable to find the configuration for " + serviceName + ".");

        return serviceConfig.apiToken();
    }

    /**
     * Uses the service name as the key to retrieve the {@link SslConfiguration} for the respective
     * {@link ServiceConfiguration}. If the {@link SslConfiguration} is not defined in the {@link ServiceConfiguration},
     * the default {@link SslConfiguration} will be returned.
     *
     * @param serviceName
     *        the name of the service to be used to retrieve the respective {@link ServiceConfiguration}
     * @return the {@link ServiceConfiguration} for a service based on the input service name
     */
    public final Optional<SslConfiguration> getSslConfiguration(String serviceName) {
        ServiceConfiguration serviceConfig =
                Preconditions.checkNotNull(
                        getServices().get(serviceName), "Unable to find the configuration for " + serviceName + ".");

        return serviceConfig.security();
    }
}
