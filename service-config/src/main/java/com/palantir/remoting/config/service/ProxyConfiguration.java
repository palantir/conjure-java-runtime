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

package com.palantir.remoting.config.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.net.HostAndPort;
import java.net.InetSocketAddress;
import java.net.Proxy;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Lazy;
import org.immutables.value.Value.Style;

@Immutable
@JsonDeserialize(as = ImmutableProxyConfiguration.class)
@Style(visibility = Style.ImplementationVisibility.PACKAGE)
public abstract class ProxyConfiguration {

    /**
     * The hostname and port of the HTTP/HTTPS Proxy. Recognized formats include those recognized by {@link
     * com.google.common.net.HostAndPort}, for instance {@code foo.com:80}, {@code 192.168.3.100:8080}, etc.
     */
    public abstract String hostAndPort();

    /**
     * Credentials if the proxy needs authentication.
     */
    public abstract Optional<BasicCredentials> credentials();

    @Value.Check
    protected final void check() {
        HostAndPort host = HostAndPort.fromString(hostAndPort());
        Preconditions.checkArgument(host.hasPort(), "Given hostname does not contain a port number: " + host);
    }

    @Lazy
    @SuppressWarnings("checkstyle:designforextension")
    @JsonIgnore
    public Proxy toProxy() {
        HostAndPort hostAndPort = HostAndPort.fromString(hostAndPort());
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hostAndPort.getHostText(), hostAndPort.getPort()));
    }

    public static ProxyConfiguration of(String hostAndPort) {
        return ImmutableProxyConfiguration.builder().hostAndPort(hostAndPort).build();
    }

    public static ProxyConfiguration of(String hostAndPort, BasicCredentials credentials) {
        return ImmutableProxyConfiguration.builder().hostAndPort(hostAndPort).credentials(credentials).build();
    }
}
