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

package com.palantir.remoting2.retrofit;

import com.google.common.base.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE, jdkOnly = true)
public abstract class OkHttpClientOptions {

    public abstract Optional<Long> getConnectTimeoutMs();

    public abstract Optional<Long> getReadTimeoutMs();

    public abstract Optional<Long> getWriteTimeoutMs();

    public abstract Optional<Integer> getMaxNumberRetries();

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends com.palantir.remoting2.retrofit.ImmutableOkHttpClientOptions.Builder {}

}
