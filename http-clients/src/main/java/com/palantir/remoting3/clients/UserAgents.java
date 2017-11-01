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
import com.google.common.collect.Lists;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UserAgents {

    private static final Logger log = LoggerFactory.getLogger(UserAgents.class);

    private static final Joiner COMMA_JOINER = Joiner.on(", ");
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
        return COMMA_JOINER.join(Lists.transform(userAgent.agents(), UserAgents::formatSingleAgent));
    }

    /**
     * Returns the {@link UserAgent} comprising all {@link UserAgent#agents} from the left and all {@link
     * UserAgent#agents} from the right given {@link UserAgent}s.
     */
    public static UserAgent merge(UserAgent left, UserAgent right) {
        return ImmutableUserAgent.builder()
                .from(left)
                .addAllAgents(right.agents())
                .build();
    }

    private static String formatSingleAgent(UserAgent.Agent agent) {
        StringBuilder builder = new StringBuilder(agent.serviceName());

        agent.instanceId().ifPresent(id -> builder.append("/").append(id));

        builder.append(" (").append(agent.version()).append(")");
        return builder.toString();
    }

    static boolean isValidServiceName(String serviceName) {
        return SERVICE_NAME_REGEX.matcher(serviceName).matches();
    }

    static boolean isValidInstance(String instanceId) {
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
