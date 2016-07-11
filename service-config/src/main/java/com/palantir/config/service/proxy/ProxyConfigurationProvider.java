/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */
package com.palantir.config.service.proxy;

import com.google.common.base.Optional;

public interface ProxyConfigurationProvider {

    Optional<ProxyConfiguration> getProxyConfiguration();

}
