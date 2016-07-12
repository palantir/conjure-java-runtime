/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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
package com.palantir.config.service;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import org.immutables.value.Value;
import org.immutables.value.Value.Check;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Style;

@Immutable
@JsonDeserialize(as = ImmutableBasicCredentials.class)
@Style(visibility = Style.ImplementationVisibility.PACKAGE)
public abstract class BasicCredentials {

    @Value.Parameter
    public abstract String username();

    @Value.Parameter
    public abstract String password();

    @Check
    protected final void check() {
        Preconditions.checkArgument(!username().isEmpty(), "Username must not be empty");
        Preconditions.checkArgument(!password().isEmpty(), "Password must not be empty");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends ImmutableBasicCredentials.Builder {}

    public static BasicCredentials of(String username, String password) {
        return ImmutableBasicCredentials.of(username, password);
    }
}
