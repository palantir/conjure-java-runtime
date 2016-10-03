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

package com.palantir.remoting1.servers.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/** Configuration options for tracing. */
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
@JsonDeserialize(as = ImmutableTracingConfig.class)
@JsonSerialize(as = ImmutableTracingConfig.class)
@SuppressWarnings("checkstyle:designforextension")
public abstract class TracingConfig {

    public abstract SamplerConfig sampler();

    public abstract ReporterTypeConfig reporter();

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends ImmutableTracingConfig.Builder {}
}
