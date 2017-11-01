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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UserAgents {

    private static final Logger log = LoggerFactory.getLogger(UserAgents.class);

    private static final Joiner COMMA_JOINER = Joiner.on(", ");
    private static final Joiner.MapJoiner COLON_SEMICOLON_JOINER = Joiner.on(';').withKeyValueSeparator(":");
    private static final Pattern SERVICE_NAME_REGEX = Pattern.compile("([a-zA-Z][a-zA-Z0-9\\-]*)");
    private static final Pattern INSTANCE_ID_REGEX = SERVICE_NAME_REGEX;
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

    static boolean isValidLibraryName(String serviceName) {
        return SERVICE_NAME_REGEX.matcher(serviceName).matches();
    }

    static boolean isValidNodeId(String instanceId) {
        return INSTANCE_ID_REGEX.matcher(instanceId).matches();
    }

    static boolean isValidVersion(String version) {
        for (Pattern p : ORDERABLE_VERSION) {
            if (p.matcher(version).matches()) {
                return true;
            }
        }
        log.warn("Encountered invalid user agent version: " + version);
        return false;
    }
}
