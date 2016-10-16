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

package com.palantir.remoting1.config.service;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Optional;
import com.palantir.remoting1.config.ssl.SslConfiguration;
import com.palantir.tokens.auth.BearerToken;
import java.util.List;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

@Immutable
@JsonDeserialize(as = ImmutableServiceConfiguration.class)
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
     * A list of service URIs.
     */
    public abstract List<String> uris();

    /**
     * Proxy configuration for connecting to the service.
     */
    public abstract Optional<ProxyConfiguration> proxyConfiguration();

    /**
     * The list of hosts we are allowed to receive cookies from.
     */
    public abstract Optional<List<String>> allowedCookieHosts();

    /**
     * The list of cookie names we are allowed to receive. It does a cookieName.matches(regex) so regex is allowed
     */
    public abstract Optional<List<String>> allowedCookieNameRegex();


    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends ImmutableServiceConfiguration.Builder {}
}
