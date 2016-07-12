/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */
package com.palantir.config.service.proxy;

/**
 * A default {@link com.palantir.config.service.proxy.ProxyConfigurationProviderChain}.
 * This chain consists of:
 * <ol>
 *     <li>{@link com.palantir.config.service.proxy.SystemPropertiesProxyConfigurationProvider}</li>
 *     <li>{@link com.palantir.config.service.proxy.EnvironmentVariableProxyConfigurationProvider}</li>
 * </ol>
 */
public final class DefaultProxyConfigurationProviderChain extends ProxyConfigurationProviderChain {

    public DefaultProxyConfigurationProviderChain() {
        super(
                new SystemPropertiesProxyConfigurationProvider(),
                new EnvironmentVariableProxyConfigurationProvider()
        );
    }
}
