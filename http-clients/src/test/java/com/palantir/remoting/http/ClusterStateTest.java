/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;

public final class ClusterStateTest {

    private static final String SERVER1 = "http://server1";
    private static final String SERVER2 = "http://server2";
    private static final String SERVER3 = "http://server3";

    private ClusterState state;

    @Before
    public void setUp() {
        state = new ClusterState(ImmutableSet.<String>of(SERVER1, SERVER2, SERVER3));
    }

    @Test
    public void testOneServer() {
        ClusterState testState = new ClusterState(ImmutableSet.<String>of(SERVER1));
        assertThat(testState.currentServer(), is(SERVER1));
        assertThat(testState.incrementFailuresForCurrentServer(), is(1));

        testState.markCurrentServerAsDown();
        assertThat(testState.trySwitchServer(), is(Optional.<String>absent()));
    }

    @Test
    public void testFailures() {
        String firstServer = state.currentServer();
        assertNotNull(firstServer);

        assertThat(state.incrementFailuresForCurrentServer(), is(1));
        assertThat(state.getFailuresForCurrentServer(), is(1));
        state.markCurrentServerAsDown();
        assertTrue(state.trySwitchServer().isPresent());
        String secondServer = state.currentServer();
        assertThat(secondServer, not(is(firstServer)));
        assertThat(state.getFailuresForCurrentServer(), is(0));

        assertThat(state.incrementFailuresForCurrentServer(), is(1));
        state.markCurrentServerAsDown();
        assertTrue(state.trySwitchServer().isPresent());
        String thirdServer = state.currentServer();
        assertThat(thirdServer, not(isIn(ImmutableSet.of(firstServer, secondServer))));
        assertThat(state.getFailuresForCurrentServer(), is(0));

        assertThat(state.incrementFailuresForCurrentServer(), is(1));
        state.markCurrentServerAsDown();
        assertFalse(state.trySwitchServer().isPresent());

        assertThat(state.incrementFailuresForCurrentServer(), is(2));
        assertThat(state.getFailuresForCurrentServer(), is(2));

        state.resetState();
        assertThat(state.getFailuresForCurrentServer(), is(0));
        assertTrue(state.trySwitchServer().isPresent());
    }

    @Test
    public void testFollowers() {
        String firstServer = state.currentServer();
        assertNotNull(firstServer);

        state.markCurrentServerAsFollower();
        assertTrue(state.trySwitchServer().isPresent());
        String secondServer = state.currentServer();
        assertThat(secondServer, not(is(firstServer)));

        state.markCurrentServerAsFollower();
        assertTrue(state.trySwitchServer().isPresent());
        String thirdServer = state.currentServer();
        assertThat(thirdServer, not(isIn(ImmutableSet.of(firstServer, secondServer))));

        state.markCurrentServerAsFollower();
        assertFalse(state.trySwitchServer().isPresent());

        state.resetFollowerState();
        assertTrue(state.trySwitchServer().isPresent());
    }

    @Test
    public void incrementLeaderSearchFailures() {
        assertThat(state.incrementFailuresForCurrentServer(), is(1));
        assertThat(state.incrementFailuresForCurrentServer(), is(2));
        state.resetFollowerState();
        assertThat(state.incrementFailuresForCurrentServer(), is(3));
        state.resetState();
        assertThat(state.incrementFailuresForCurrentServer(), is(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidServers() {
        new ClusterState(ImmutableSet.<String>of());
    }
}
