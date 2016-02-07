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

package com.palantir.ssl;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Optional;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;

@JsonDeserialize(as = ImmutableSslConfiguration.class)
@JsonSerialize(as = ImmutableSslConfiguration.class)
@Value.Immutable
@Value.Style(visibility = ImplementationVisibility.PACKAGE)
public abstract class SslConfiguration {

    public abstract Path trustStorePath();

    public abstract Optional<String> trustStoreType();

    public abstract Optional<String> trustStorePassword();

    public abstract Optional<Path> keyStorePath();

    public abstract Optional<String> keyStoreType();

    public abstract Optional<String> keyStorePassword();

    @Value.Check
    protected final void check() {
        checkArgument(!trustStorePath().equals(Paths.get("")), "trustStorePath cannot be empty");
        checkArgument(!(keyStorePath().isPresent() && keyStorePath().get().equals(Paths.get(""))),
                "keyStorePath cannot be empty");
        checkArgument(!(keyStorePath().isPresent() && !keyStorePassword().isPresent()),
                "keyStorePassword cannot be absent if keyStorePath is present");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ImmutableSslConfiguration.Builder {}

}
