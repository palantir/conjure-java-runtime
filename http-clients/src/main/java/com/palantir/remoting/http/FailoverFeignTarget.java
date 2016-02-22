/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.http;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import feign.Client;
import feign.Request;
import feign.Request.Options;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.Target;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A combined Feign {@link Target} and {@link Retryer} designed to provide failover across a list of service targets
 * instead of iterative retries against a single, fixed target. Upon detecting a failure in a service call, the retry
 * strategy will cycle through a provided collection of service URIs with a provided backoff strategy to prevent server
 * call amplification due to individual server failures. Implementation detail: failover state is shared via
 * thread-local variables, using the fact that Feign calls {@link #clone} to initialize the retryer before the first
 * connection attempt.
 *
 * This class also supports a cluster of servers with a designated leader. Upon receiving a {@link NotLeaderException}
 * when trying a request it will immediately switch to another server. If all servers have failed or are in follower
 * mode, it is assumed that the cluster is attempting to elect a new leader (as long as there is at least one follower).
 * In this case more attempts to find the leader can be made, as governed by the provided
 * {@link #leaderElectionBackoffStrategy}.
 */
@SuppressWarnings("checkstyle:noclone")
public final class FailoverFeignTarget<T> implements Target<T>, Retryer {
    private static final Logger log = LoggerFactory.getLogger(FailoverFeignTarget.class);

    private final String name;
    private final Class<T> type;
    private final BackoffStrategy backoffStrategy;
    private final BackoffStrategy leaderElectionBackoffStrategy;

    /** Keeps track of the state of all servers in the cluster, in the scope of a single request. */
    private ThreadLocal<ClusterState> clusterState;

    /**
     * Constructs a new instance for the given server list; retries against the same server are governed by the given
     * {@link BackoffStrategy}, i.e., the next server is tried as soon as {@link BackoffStrategy#backoff} returns {@code
     * false}. Each server is tried at most once in between successive {@link #clone} calls.
     */
    public FailoverFeignTarget(Collection<String> servers, Class<T> type, BackoffStrategy backoffStrategy,
            BackoffStrategy leaderElectionBackoffStrategy) {
        final List<String> shuffledServers = new ArrayList<>(servers);
        Collections.shuffle(shuffledServers);

        this.name = FailoverFeignTarget.class.getSimpleName() + " instance with servers: " + shuffledServers;
        this.type = type;
        this.backoffStrategy = backoffStrategy;
        this.leaderElectionBackoffStrategy = leaderElectionBackoffStrategy;
        this.clusterState = new ThreadLocal<ClusterState>() {
            @Override
            protected ClusterState initialValue() {
                return new ClusterState(ImmutableSet.copyOf(shuffledServers));
            }
        };
    }

    @Override
    public void continueOrPropagate(RetryableException exception) {
        ClusterState state = clusterState.get();
        String currentServer = state.currentServer();

        if (exception instanceof NotLeaderException) {
            log.info("Marking {} as follower.", currentServer);
            state.markCurrentServerAsFollower();
        } else if (backoffStrategy.backoff(state.incrementFailuresForCurrentServer())) {
            log.warn("{}: {}. Attempt #{} failed for server {}. Retrying the same server.",
                    exception.getCause(), exception.getMessage(), state.getFailuresForCurrentServer(), currentServer);
            // Use same server again.
            return;
        } else {
            log.error("{}: {}. Server {} failed {} times, marking it as down.", exception.getCause(),
                    exception.getMessage(), currentServer, state.getFailuresForCurrentServer());
            state.markCurrentServerAsDown();
        }
        // Get next server or fail if all servers have failed.
        if (!findNewServer()) {
            // Attempted to call all servers - propagate exception.
            // Note: Not resetting state here since Feign calls clone() before re-using this retryer.
            throw exception;
        }
        log.info("Retrying using server: {}.", state.currentServer());
    }

    private boolean findNewServer() {
        ClusterState state = clusterState.get();
        Optional<String> newServer = state.trySwitchServer();

        if (newServer.isPresent()) {
            return true;
        } else if (state.hasFollowerNodes()) {
            int failedLeaderSearchAttempts = state.incrementLeaderSearchFailures();
            if (leaderElectionBackoffStrategy.backoff(failedLeaderSearchAttempts)) {
                log.warn("No leader found after trying all servers. Retrying.");
                state.resetFollowerState();
                return state.trySwitchServer().isPresent();
            }
            log.error("No leader node found after {} attempts.", failedLeaderSearchAttempts);
        }
        return false;
    }

    @SuppressWarnings("checkstyle:superclone")
    @Override
    public Retryer clone() {
        clusterState.get().resetState();
        return this;
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String url() {
        return clusterState.get().currentServer();
    }

    @Override
    public Request apply(RequestTemplate input) {
        if (input.url().indexOf("http") != 0) {
            input.insert(0, url());
        }
        return input.request();
    }

    public Client wrapClient(final Client client) {
        return new Client() {
            @Override
            public Response execute(Request request, Options options) throws IOException {
                Response response = client.execute(request, options);
                if (response.status() >= 200 && response.status() < 300) {
                    clusterState.get().resetState();
                }
                return response;
            }
        };
    }
}
