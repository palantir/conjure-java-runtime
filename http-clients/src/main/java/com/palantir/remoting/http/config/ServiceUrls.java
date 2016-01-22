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

package com.palantir.remoting.http.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Set;
import org.immutables.value.Value;

/**
 * Configuration for a service that can be accessible by both public DNS and several internal URLs.
 */
@JsonSerialize(as = ImmutableServiceUrls.class)
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class ServiceUrls {

    @JsonCreator
    public static ServiceUrls of(String url) {
        return ImmutableServiceUrls.builder()
            .externalUrl(url)
            .addInternalUrls(url)
            .build();
    }

    @JsonCreator
    public static ServiceUrls of(
            @JsonProperty("externalUrl") String externalUrl,
            @JsonProperty("internalUrls") Set<String> internalUrls) {

        return ImmutableServiceUrls.builder()
                .addAllInternalUrls(internalUrls)
                .externalUrl(externalUrl)
                .build();
    }

    public abstract Set<String> getInternalUrls();

    public abstract String getExternalUrl();
}
