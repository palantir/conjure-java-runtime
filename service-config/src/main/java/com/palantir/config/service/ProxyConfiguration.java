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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Optional;
import java.net.InetSocketAddress;
import java.net.Proxy;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Lazy;
import org.immutables.value.Value.Style;

@Immutable
@JsonDeserialize(as = ImmutableProxyConfiguration.class)
@Style(visibility = Style.ImplementationVisibility.PACKAGE)
public abstract class ProxyConfiguration {

    /**
     * The hostname of the HTTP/HTTPS Proxy.
     */
    public abstract String host();

    /**
     * Port for the proxy.
     */
    public abstract int port();

    /**
     * Credentials if the proxy needs authentication.
     */
    public abstract Optional<BasicCredentials> credentials();

    @Lazy
    @SuppressWarnings("checkstyle:designforextension")
    @JsonIgnore
    public Proxy toProxy() {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host(), port()));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends ImmutableProxyConfiguration.Builder {}
}
