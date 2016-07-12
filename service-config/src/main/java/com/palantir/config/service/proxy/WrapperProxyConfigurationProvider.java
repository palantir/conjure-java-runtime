/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */
package com.palantir.config.service.proxy;

import com.google.common.base.Optional;

/**
 * A simple {@link com.palantir.config.service.proxy.ProxyConfigurationProvider} wrapping a provided optional
 * {@link com.palantir.config.service.proxy.ProxyConfiguration}.
 */
public final class WrapperProxyConfigurationProvider implements ProxyConfigurationProvider {

    private final Optional<ProxyConfiguration> proxyConfiguration;

    public WrapperProxyConfigurationProvider(Optional<ProxyConfiguration> proxyConfiguration) {
        this.proxyConfiguration = proxyConfiguration;
    }

    @Override
    public Optional<ProxyConfiguration> getProxyConfiguration() {
        return proxyConfiguration;
    }
}
