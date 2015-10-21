/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.trust;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Optional;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;

@JsonDeserialize(as = ImmutableTrustStoreConfiguration.class)
@JsonSerialize(as = ImmutableTrustStoreConfiguration.class)
@Value.Immutable
@Value.Style(visibility = ImplementationVisibility.PACKAGE)
public abstract class TrustStoreConfiguration {

    public abstract Path trustStorePath();

    public abstract Optional<String> trustStoreType();

    public abstract Optional<String> trustStorePassword();

    @Value.Check
    protected final void check() {
        checkArgument(!trustStorePath().equals(Paths.get("")), "trustStorePath cannot be empty");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ImmutableTrustStoreConfiguration.Builder {}

}
