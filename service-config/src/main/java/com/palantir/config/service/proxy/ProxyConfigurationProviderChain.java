/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */
package com.palantir.config.service.proxy;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Optional;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link com.palantir.config.service.proxy.ProxyConfigurationProvider} that returns the first non-empty
 * {@link com.palantir.config.service.proxy.ProxyConfiguration} defined in the chain.
 */
public class ProxyConfigurationProviderChain implements ProxyConfigurationProvider {

    private static final Logger logger = LoggerFactory.getLogger(ProxyConfigurationProviderChain.class);

    private final List<ProxyConfigurationProvider> providers = new LinkedList<>();

    public ProxyConfigurationProviderChain(ProxyConfigurationProvider... providers) {
        checkState(providers != null && providers.length > 0,
                "At least one ProxyConfigurationProvider must be provided");
        Collections.addAll(this.providers, providers);
    }

    @Override
    public final Optional<ProxyConfiguration> getProxyConfiguration() {
        for (ProxyConfigurationProvider provider : providers) {
            try {
                Optional<ProxyConfiguration> configuration = provider.getProxyConfiguration();
                if (configuration.isPresent()) {
                    logger.debug("Loading configuration from {}", provider);
                    return configuration;
                }
            } catch (Exception e) {
                logger.debug("Unable to load configuration from {}", provider, e);
            }
        }
        return Optional.<ProxyConfiguration>absent();
    }
}
