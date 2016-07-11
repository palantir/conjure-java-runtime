/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */
package com.palantir.config.service.proxy;

import com.google.common.base.Optional;

/**
 * A simple {@link com.palantir.config.service.proxy.ProxyConfigurationProvider} providing an empty
 * {@link com.palantir.config.service.proxy.ProxyConfiguration}.
 */
public final class EmptyProxyConfigurationProvider implements ProxyConfigurationProvider {

    @Override
    public Optional<ProxyConfiguration> getProxyConfiguration() {
        return Optional.absent();
    }
}
