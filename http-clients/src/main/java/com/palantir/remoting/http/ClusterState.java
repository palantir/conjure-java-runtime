/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps track of the state of a cluster of servers, within the scope of a Feign request. It allows
 * to mark servers as down or in follower state and to keep track of the number of retries for
 * the current server.
 *
 * This class is not thread-safe and it is meant to be used as a thread local variable.
 */
public final class ClusterState {
    private enum ServerState {
        UNKNOWN,
        DOWN,
        FOLLOWER
    }

    /** The server to be used for the next request. */
    private String currentServer;
    /** A map of server url to the corresponding state. */
    private Map<String, ServerState> serverStates;
    /** Counts the number of failed connection attempts for the {@link #currentServer current server}. */
    private int failedAttemptsForCurrentServer;
    /** Counts the number of failed leader search attempts. */
    private int failedLeaderSearchAttempts;

    public ClusterState(Set<String> servers) {
        Preconditions.checkArgument(servers != null && servers.size() > 0, "must specify at least one server");
        this.failedAttemptsForCurrentServer = 0;
        this.failedLeaderSearchAttempts = 0;
        this.serverStates = createServerStates(servers);
        this.currentServer = shuffleServers().get(0);
    }

    public String currentServer() {
        return currentServer;
    }

    public int getFailuresForCurrentServer() {
        return failedAttemptsForCurrentServer;
    }

    public int incrementFailuresForCurrentServer() {
        return ++failedAttemptsForCurrentServer;
    }

    public int incrementLeaderSearchFailures() {
        return ++failedLeaderSearchAttempts;
    }

    public void markCurrentServerAsFollower() {
        serverStates.put(currentServer, ServerState.FOLLOWER);
    }

    public void markCurrentServerAsDown() {
        serverStates.put(currentServer, ServerState.DOWN);
    }

    public boolean hasFollowerNodes() {
        return getServerByState(shuffleServers(), ServerState.FOLLOWER).isPresent();
    }

    /**
     * Attempts to switch to a server that we have not contacted yet.
     *
     * @return the new server if it was possible to switch
     */
    public Optional<String> trySwitchServer() {
        Optional<String> newServer = getServerByState(shuffleServers(), ServerState.UNKNOWN);

        if (newServer.isPresent()) {
            failedAttemptsForCurrentServer = 0;
            currentServer = newServer.get();
        }
        return newServer;
    }

    /**
     * Resets all state except for "currentServer" so that the next request still uses the same server.
     */
    public void resetState() {
        serverStates = createServerStates(serverStates.keySet());
        failedAttemptsForCurrentServer = 0;
        failedLeaderSearchAttempts = 0;
    }

    /**
     * Resets the state for nodes that were marked as followers.
     */
    public void resetFollowerState() {
        serverStates = new HashMap<>(Maps.transformValues(serverStates, new Function<ServerState, ServerState>() {

            @Override
            public ServerState apply(ServerState state) {
                return state == ServerState.FOLLOWER ? ServerState.UNKNOWN : state;
            }
        }));
    }

    private Map<String, ServerState> createServerStates(Set<String> urls) {
        return new HashMap<>(Maps.asMap(urls, Functions.constant(ServerState.UNKNOWN)));
    }

    private List<String> shuffleServers() {
        List<String> servers = Lists.newArrayList(serverStates.keySet());
        Collections.shuffle(servers);
        return servers;
    }

    private Optional<String> getServerByState(List<String> options, final ServerState state) {
        return Iterables.tryFind(options, new Predicate<String>() {

            @Override
            public boolean apply(String input) {
                return serverStates.get(input) == state;
            }
        });
    }
}
