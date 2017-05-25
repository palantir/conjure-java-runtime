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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import com.google.common.net.HostAndPort;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Optional;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Lazy;
import org.immutables.value.Value.Style;

@Immutable
@JsonDeserialize(builder = ProxyConfiguration.Builder.class)
@JsonSerialize(as = ImmutableProxyConfiguration.class)
@Style(visibility = Style.ImplementationVisibility.PACKAGE, builder = "new")
public abstract class ProxyConfiguration {

    enum Type {

        /**
         * Use a direct connection. This option will bypass any JVM-level configured proxy settings.
         */
        DIRECT,

        /**
         * Use an http-proxy specified by {@link ProxyConfiguration#hostAndPort()}  and (optionally)
         * {@link ProxyConfiguration#credentials()}.
         */
        HTTP;
    }

    /**
     * The hostname and port of the HTTP/HTTPS Proxy. Recognized formats include those recognized by {@link
     * com.google.common.net.HostAndPort}, for instance {@code foo.com:80}, {@code 192.168.3.100:8080}, etc.
     */
    @JsonProperty("hostAndPort")
    public abstract Optional<String> maybeHostAndPort();

    /**
     * @deprecated Use maybeHostAndPort().
     */
    @Deprecated
    @SuppressWarnings("checkstyle:designforextension")
    @JsonIgnore
    @Lazy
    public String hostAndPort() {
        Preconditions.checkState(maybeHostAndPort().isPresent(), "hostAndPort was not configured");
        return maybeHostAndPort().get();
    }

    /**
     * Credentials if the proxy needs authentication.
     */
    public abstract Optional<BasicCredentials> credentials();

    /**
     * The type of Proxy.  Defaults to {@link Type#HTTP}.
     */
    @Value.Default
    @SuppressWarnings("checkstyle:designforextension")
    public Type type() {
        return Type.HTTP;
    }

    @Value.Check
    protected final void check() {
        switch (type()) {
            case HTTP:
                Preconditions.checkArgument(maybeHostAndPort().isPresent(), "host-and-port must be "
                        + "configured for an HTTP proxy");
                HostAndPort host = HostAndPort.fromString(maybeHostAndPort().get());
                Preconditions.checkArgument(host.hasPort(),
                        "Given hostname does not contain a port number: " + host);
                break;
            case DIRECT:
                Preconditions.checkArgument(!maybeHostAndPort().isPresent() && !credentials().isPresent(),
                        "Neither credential nor host-and-port may be configured for DIRECT proxies");
                break;
            default:
                throw new IllegalStateException("Unrecognized case; this is a library bug");
        }

        if (credentials().isPresent()) {
            Preconditions.checkArgument(type() == Type.HTTP, "credentials only valid for http proxies");
        }
    }

    @Lazy
    @SuppressWarnings("checkstyle:designforextension")
    @JsonIgnore
    public Proxy toProxy() {
        switch (type()) {
            case HTTP:
                HostAndPort hostAndPort = HostAndPort.fromString(maybeHostAndPort().get());
                InetSocketAddress addr = new InetSocketAddress(hostAndPort.getHostText(), hostAndPort.getPort());
                return new Proxy(Proxy.Type.HTTP, addr);
            case DIRECT:
                return Proxy.NO_PROXY;
            default:
                throw new IllegalStateException("unrecognized proxy type; this is a library error");
        }
    }

    public static ProxyConfiguration of(String hostAndPort) {
        return new ProxyConfiguration.Builder().maybeHostAndPort(hostAndPort).build();
    }

    public static ProxyConfiguration of(String hostAndPort, BasicCredentials credentials) {
        return new ProxyConfiguration.Builder().maybeHostAndPort(hostAndPort).credentials(credentials).build();
    }

    public static ProxyConfiguration direct() {
        return new ProxyConfiguration.Builder().type(Type.DIRECT).build();
    }

    // TODO(jnewman): #317 - remove kebab-case methods when Jackson 2.7 is picked up
    static final class Builder extends ImmutableProxyConfiguration.Builder {

        @JsonProperty("host-and-port")
        Builder hostAndPortKebabCase(String hostAndPort) {
            return maybeHostAndPort(hostAndPort);
        }
    }
}
