/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */
package com.palantir.remoting.http;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Optional;
import com.palantir.config.service.proxy.ProxyConfigurationProvider;
import javax.net.ssl.SSLSocketFactory;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

@Immutable
@JsonDeserialize(as = ImmutableClientConfiguration.class)
@Style(visibility = Style.ImplementationVisibility.PACKAGE)
public abstract class ClientConfiguration {

    /**
     * An optional {@link javax.net.ssl.SSLSocketFactory} for the client to use.
     */
    public abstract Optional<SSLSocketFactory> sslSocketFactory();

    /**
     * An {@link com.palantir.config.service.proxy.ProxyConfigurationProvider} providing optional configuration for a
     * proxy.
     */
    public abstract ProxyConfigurationProvider proxyConfigurationProvider();

    /**
     * A user agent.
     */
    public abstract String userAgent();

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends ImmutableClientConfiguration.Builder {}
}
