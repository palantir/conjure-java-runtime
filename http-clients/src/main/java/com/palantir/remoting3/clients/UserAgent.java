/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.clients;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.immutables.value.Value;

/**
 * Constructs, validates, and formats a canonical User-Agent header. Because the http header spec
 * (https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2) requires headers to be joined on commas, individual
 * {@link Agent} header strings must never contain commas.
 */
@Value.Immutable
@ImmutablesStyle
public interface UserAgent {
    List<Agent> agents();

    @Value.Lazy
    default String headerFormat() {
        return Joiner.on(", ").join(Lists.transform(agents(), Agent::headerFormat));
    }

    static UserAgent of(String serviceName, String instanceId, String version) {
        Agent agent = ImmutableAgent.builder()
                .serviceName(serviceName)
                .instanceId(instanceId)
                .version(version)
                .build();
        return ImmutableUserAgent.builder()
                .addAgents(agent)
                .build();
    }

    static UserAgent of(String serviceName, String version) {
        Agent agent = ImmutableAgent.builder()
                .serviceName(serviceName)
                .version(version)
                .build();
        return ImmutableUserAgent.builder()
                .addAgents(agent)
                .build();
    }

    @Value.Default
    default UserAgent append(String serviceName, String version) {
        Agent agent = ImmutableAgent.builder()
                .serviceName(serviceName)
                .version(version)
                .build();
        return ImmutableUserAgent.builder()
                .from(this)
                .addAgents(agent)
                .build();
    }

    @Value.Immutable
    @ImmutablesStyle
    interface Agent {
        Pattern SERVICE_NAME_REGEX = Pattern.compile("([a-zA-Z][a-zA-Z0-9\\-]*)");
        Pattern INSTANCE_ID_REGEX = SERVICE_NAME_REGEX;
        Pattern VERSION_REGEX = Pattern.compile("([0-9][a-z0-9\\-\\.]*)");

        String serviceName();
        Optional<String> instanceId();
        String version();

        @Value.Lazy
        default String headerFormat() {
            StringBuilder builder = new StringBuilder(serviceName());

            instanceId().ifPresent(id -> builder.append("/").append(id));

            builder.append(" (").append(version()).append(")");
            return builder.toString();
        }

        @Value.Check
        default void check() {
            checkArgument(SERVICE_NAME_REGEX.matcher(serviceName()).matches(),
                    "Illegal service name format: %s", serviceName());
            if (instanceId().isPresent()) {
                checkArgument(INSTANCE_ID_REGEX.matcher(instanceId().get()).matches(),
                        "Illegal instance id format: %s", instanceId().get());
            }
            checkArgument(VERSION_REGEX.matcher(version()).matches(),
                    "Illegal version format: %s", version());
        }
    }
}
