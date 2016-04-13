/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.ssl;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;

@JsonDeserialize(as = ImmutableSslConfiguration.class)
@JsonSerialize(as = ImmutableSslConfiguration.class)
@Value.Immutable
@Value.Style(visibility = ImplementationVisibility.PACKAGE)
public abstract class SslConfiguration {

    public enum StoreType {
        JKS,
        PEM,
        PKCS12,
        PUPPET
    }

    private static final StoreType DEFAULT_STORE_TYPE = StoreType.JKS;

    public abstract Path trustStorePath();

    @SuppressWarnings("checkstyle:designforextension")
    @Value.Default
    public StoreType trustStoreType() {
        return DEFAULT_STORE_TYPE;
    }

    public abstract Optional<Path> keyStorePath();

    public abstract Optional<String> keyStorePassword();

    @SuppressWarnings("checkstyle:designforextension")
    @Value.Default
    public StoreType keyStoreType() {
        return DEFAULT_STORE_TYPE;
    }

    // alias of the key that should be used in the key store.
    // If absent, first entry returned by key store is used.
    public abstract Optional<String> keyStoreKeyAlias();

    @Value.Check
    protected final void check() {
        Preconditions.checkArgument(
                keyStorePath().isPresent() == keyStorePassword().isPresent(),
                "keyStorePath and keyStorePassword must both be present or both be absent");

        Preconditions.checkArgument(
                !keyStoreKeyAlias().isPresent() || keyStorePath().isPresent(),
                "keyStorePath must be present if keyStoreKeyAlias is present");
    }

    public static SslConfiguration of(Path trustStorePath) {
        return SslConfiguration.builder().trustStorePath(trustStorePath).build();
    }

    public static SslConfiguration of(Path trustStorePath, Path keyStorePath, String keyStorePassword) {
        return SslConfiguration.builder()
                .trustStorePath(trustStorePath)
                .keyStorePath(keyStorePath)
                .keyStorePassword(keyStorePassword)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ImmutableSslConfiguration.Builder {}

}
