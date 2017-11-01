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

import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Constructs, validates, and formats a canonical User-Agent header. Because the http header spec
 * (https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2) requires headers to be joined on commas, individual
 * {@link Agent} header strings must never contain commas.
 */
@Value.Immutable
@ImmutablesStyle
public interface UserAgent {

    /** Identifies the node (e.g., IP address, container identifier, etc) on which this user agent was constructed. */
    Optional<String> nodeId();

    /** The primary user agent, typically the name/version of the service initiating an RPC call. */
    Agent primary();

    /**
     * A list of additional libraries that participate (client-side) in the RPC call, for instance RPC libraries, API
     * JARs, etc.
     */
    List<Agent> informational();

    /** Creates a new {@link UserAgent} with the given {@link #primary} agent and originating node id. */
    static UserAgent of(Agent agent, String nodeId) {
        return ImmutableUserAgent.builder()
                .nodeId(nodeId)
                .primary(agent)
                .build();
    }

    /**
     * Like {@link #of(Agent, String)}, but with an empty/unknown node id. Users should generally prefer the version
     * with explicit node in order to facilitate server-side client trackingb
     */
    static UserAgent of(Agent agent) {
        return ImmutableUserAgent.builder().primary(agent).build();
    }

    /**
     * Returns a new {@link UserAgent} instance whose {@link #informational} agents are this instance's agents plus the
     * given agent.
     */
    default UserAgent addAgent(Agent agent) {
        return ImmutableUserAgent.builder()
                .from(this)
                .addInformational(agent)
                .build();
    }

    @Value.Check
    default void check() {
        if (nodeId().isPresent()) {
            checkArgument(UserAgents.isValidNodeId(nodeId().get()),
                    "Illegal node id format: %s", nodeId().get());
        }
    }

    /** Specifies an agent that participates (client-side) in an RPC call in terms of its name and version. */
    @Value.Immutable
    @ImmutablesStyle
    interface Agent {
        String DEFAULT_VERSION = "0.0.0";

        String name();
        String version();

        @Value.Check
        default void check() {
            checkArgument(UserAgents.isValidLibraryName(name()), "Illegal agent name format: %s", name());
            // Should never hit the following.
            checkArgument(UserAgents.isValidVersion(version()), "Illegal version format: %s. This is a bug", version());
        }

        static Agent of(String name, String version) {
            return ImmutableAgent.builder()
                    .name(name)
                    .version(UserAgents.isValidVersion(version) ? version : DEFAULT_VERSION)
                    .build();
        }
    }
}
