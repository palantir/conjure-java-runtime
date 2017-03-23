/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting2.http2;

import com.ea.agentloader.AgentLoader;
import java.lang.instrument.Instrumentation;
import org.mortbay.jetty.alpn.agent.Premain;

/** A very simple wrapper around the jetty-alpn-agent for dynamic loading. */
public final class Http2Agent {
    private Http2Agent() {}

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

        AgentLoader.loadAgentClass(Http2Agent.class.getName(), "");
        hasBeenInstalled = true;
    }

    /** Agent entry-point. */
    public static void agentmain(String args, Instrumentation inst) throws Exception {
        Premain.premain(args, inst);
    }
}
