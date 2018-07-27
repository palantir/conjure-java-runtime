/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.http2;

import com.ea.agentloader.AgentLoader;
import java.lang.instrument.Instrumentation;
import org.mortbay.jetty.alpn.agent.Premain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A very simple wrapper around the jetty-alpn-agent for dynamic loading. */
public final class Http2Agent {
    private Http2Agent() {}

    private static final Logger log = LoggerFactory.getLogger(Http2Agent.class);

    private static boolean hasBeenInstalled = false;

    /**
     * Installs the jetty-alpn-agent dynamically.
     * <p>
     * This method protects itself from multiple invocations so may be called in multiple places, but will only
     * ever invoke the installation once.
     */
    public static synchronized void install() {
        if (hasBeenInstalled) {
            return;
        }

        try {
            AgentLoader.loadAgentClass(Http2Agent.class.getName(), "");
            hasBeenInstalled = true;
        } catch (Exception e) {
            log.warn("Unable to dynamically install jetty-alpn-agent via ea-agent-loader, proceeding anyway...", e);
        }
    }

    /** Agent entry-point. */
    public static void agentmain(String args, Instrumentation inst) throws Exception {
        Premain.premain(args, inst);
    }
}
