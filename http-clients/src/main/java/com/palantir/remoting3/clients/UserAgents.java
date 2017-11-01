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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UserAgents {

    private static final Logger log = LoggerFactory.getLogger(UserAgents.class);

    private static final Joiner COMMA_JOINER = Joiner.on(", ");
    private static final Joiner.MapJoiner COLON_SEMICOLON_JOINER = Joiner.on(';').withKeyValueSeparator(":");
    private static final Splitter.MapSplitter COLON_SEMICOLON_SPLITTER = Splitter.on(';').withKeyValueSeparator(":");
    private static final Splitter COMMA_SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();
    private static final Pattern SERVICE_NAME_REGEX = Pattern.compile("([a-zA-Z][a-zA-Z0-9\\-]*)");
    private static final Pattern NODE_ID_REGEX = SERVICE_NAME_REGEX;
    private static final Pattern[] ORDERABLE_VERSION = new Pattern[] {
            Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+-[0-9]+-g[a-f0-9]+$"),
            Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+$"),
            Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+-rc[0-9]+$"),
            Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+-rc[0-9]+-[0-9]+-g[a-f0-9]+$")
    };

    private UserAgents() {}

    /** Returns the canonical string format for the given {@link UserAgent}. */
    public static String format(UserAgent userAgent) {
        Map<String, String> primaryComments = userAgent.nodeId().isPresent()
                ? ImmutableMap.of("nodeId", userAgent.nodeId().get())
                : ImmutableMap.of();
        return COMMA_JOINER.join(Iterables.concat(
                ImmutableList.of(formatSingleAgent(userAgent.primary(), primaryComments)),
                Lists.transform(userAgent.informational(), a -> formatSingleAgent(a, ImmutableMap.of()))));
    }

    /**
     * Formats the given agent in the form {@code name/version (key:value; key:value)}, where the ()-block of comments
     * is omitted if zero comments are provided.
     */
    private static String formatSingleAgent(UserAgent.Agent agent, Map<String, String> comments) {
        // TODO(rfink): Think about validation comments here? Must not contain special characters.
        StringBuilder formatted = new StringBuilder()
                .append(agent.name())
                .append("/")
                .append(agent.version());

        String formattedComments = COLON_SEMICOLON_JOINER.join(comments);
        if (!formattedComments.isEmpty()) {
            formatted.append(" (")
                    .append(formattedComments)
                    .append(')');
        }
        return formatted.toString();
    }


    /**
     * Parses the given string into a {@link UserAgent} or throws an {@link IllegalArgumentException} if the string does
     * not conform to the user agent syntax restrictions.
     */
    public static UserAgent parse(String userAgent) {
        return parseInternal(userAgent, false /* strict */);
    }

    /** Like {@link #parse}, but returns only the syntactically correct agent parts (, possibly zero). */
    public static UserAgent tryParse(String userAgent) {
        return parseInternal(userAgent, true /* lenient*/);
    }

    private static UserAgent parseInternal(String userAgent, boolean lenient) {
        ImmutableUserAgent.Builder builder = ImmutableUserAgent.builder();
        List<String> parts = COMMA_SPLITTER.splitToList(userAgent);
        if (parts.isEmpty()) {
            if (lenient) {
                log.warn("Empty user agent, falling back to default/unknown agent");
                return builder.primary(UserAgent.Agent.of("unknown", UserAgent.Agent.DEFAULT_VERSION)).build();
            } else {
                throw new IllegalArgumentException("Empty user agents are not allowed: " + userAgent);
            }
        }

        // Primary agent with optional nodeId
        Map<String, String> primaryComments = Maps.newHashMap();
        Optional<UserAgent.Agent> primaryAgent = tryParseSingleAgent(parts.get(0), primaryComments);
        if (primaryAgent.isPresent()) {
            builder.primary(primaryAgent.get());
        } else {
            if (lenient) {
                log.warn("Malformed primary user agent", UnsafeArg.of("agent", parts.get(0)));
                builder.primary(UserAgent.Agent.of("unknown", UserAgent.Agent.DEFAULT_VERSION));
            } else {
                throw new IllegalArgumentException("Agent string was malformed: " + parts.get(0));
            }
        }
        if (primaryComments.containsKey("nodeId")) {
            builder.nodeId(primaryComments.get("nodeId"));
        }

        // Informational agents
        for (String part : parts.subList(1, parts.size())) {
            Optional<UserAgent.Agent> informationlAgent = tryParseSingleAgent(part, Maps.newHashMap());
            if (informationlAgent.isPresent()) {
                builder.addInformational(informationlAgent.get());
            } else {
                if (lenient) {
                    // Ignore malformed agent
                    log.warn("Cannot parse malformed user agent string", UnsafeArg.of("agent", part));
                } else {
                    throw new IllegalArgumentException("Agent string was malformed: " + part);
                }
            }
        }
        return builder.build();
    }

    // returns found comments in the provided map.
    private static Optional<UserAgent.Agent> tryParseSingleAgent(String agent, Map<String, String> comments) {
        int slashPos = agent.indexOf('/');
        if (slashPos == -1) {
            return Optional.empty(); // no version
        }
        int spacePos = agent.indexOf(" (");
        String name = agent.substring(0, slashPos);
        String version = agent.substring(slashPos + 1, spacePos == -1 ? agent.length() : spacePos);

        // Extract comments
        if (spacePos != -1) {
            String commentString = agent.substring(spacePos + 2, agent.length() - 1);
            COLON_SEMICOLON_SPLITTER.split(commentString).forEach(comments::put);
        }

        return Optional.of(UserAgent.Agent.of(name, version));
    }

    static boolean isValidName(String serviceName) {
        return SERVICE_NAME_REGEX.matcher(serviceName).matches();
    }

    static boolean isValidNodeId(String instanceId) {
        return NODE_ID_REGEX.matcher(instanceId).matches();
    }

    static boolean isValidVersion(String version) {
        for (Pattern p : ORDERABLE_VERSION) {
            if (p.matcher(version).matches()) {
                return true;
            }
        }
        log.warn("Encountered invalid user agent version", SafeArg.of("version", version));
        return false;
    }
}
