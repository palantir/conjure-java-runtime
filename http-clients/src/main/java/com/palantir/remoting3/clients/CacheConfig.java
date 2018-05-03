/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.clients;

import java.io.File;
import org.immutables.value.Value;

@Value.Immutable
@ImmutablesStyle
public interface CacheConfig {

    File directory();

    long maxSizeMb();

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends ImmutableCacheConfig.Builder {}

}

