/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.clients;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.File;
import org.immutables.value.Value;

@Value.Immutable
@ImmutablesStyle
@JsonSerialize(as = ImmutableCacheConfig.class)
@JsonDeserialize(as = ImmutableCacheConfig.class)
public interface CacheConfig {

    File directory();

    long maxSizeMb();

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends ImmutableCacheConfig.Builder {}

}

