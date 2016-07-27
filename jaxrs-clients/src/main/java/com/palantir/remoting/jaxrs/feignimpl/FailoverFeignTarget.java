/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.jaxrs.feignimpl;

import com.google.common.collect.ImmutableList;
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
 */
@SuppressWarnings("checkstyle:noclone")
public final class FailoverFeignTarget<T> implements Target<T>, Retryer {

    private class ThreadLocalInteger extends ThreadLocal<Integer> {
        @Override
        protected Integer initialValue() {
            return 0;
        }

        public int increment() {
            set(get() + 1);
            return get();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(FailoverFeignTarget.class);

    private final ImmutableList<String> servers;
    private final Class<T> type;
    private final BackoffStrategy backoffStrategy;

    /** An index into {@link #servers} indicating the server to be used for the next request. */
    private ThreadLocalInteger currentServer = new ThreadLocalInteger();
    /** Counts the number of failed servers since the last successful connection attempt. */
    private ThreadLocalInteger failedServers = new ThreadLocalInteger();
    /** Counts the number of failed connection attempts for the {@link #currentServer current server}. */
    private ThreadLocalInteger failedAttemptsForCurrentServer = new ThreadLocalInteger();

    /**
     * Constructs a new instance for the given server list; retries against the same server are governed by the given
     * {@link BackoffStrategy}, i.e., the next server is tried as soon as {@link BackoffStrategy#backoff} returns {@code
     * false}. Each server is tried at most once in between successive {@link #clone} calls.
     */
    public FailoverFeignTarget(Collection<String> servers, Class<T> type, BackoffStrategy backoffStrategy) {
        List<String> shuffledServers = new ArrayList<>(servers);
        Collections.shuffle(shuffledServers);

        this.servers = ImmutableList.copyOf(shuffledServers);
        this.type = type;
        this.backoffStrategy = backoffStrategy;
    }

    @Override
    public void continueOrPropagate(RetryableException exception) {
        failedAttemptsForCurrentServer.increment();
        if (backoffStrategy.backoff(failedAttemptsForCurrentServer.get())) {
            // Use same server again.
            log.info("{}: {}. Attempt #{} failed for server {}. Retrying the same server.",
                    exception.getCause(), exception.getMessage(), failedAttemptsForCurrentServer.get(),
                    servers.get(currentServer.get()));
        } else {
            // Use next server or fail if all servers have failed.
            failedServers.increment();

            if (failedServers.get() >= servers.size()) {
                // Attempted to call all servers - propagate exception.
                // Note: Not resetting state here since Feign calls clone() before re-using this retryer.
                throw exception;
            } else {
                // Call next server in list.
                log.info("{}: {}. Server #{} ({}) failed {} times - trying next server", exception.getCause(),
                        exception.getMessage(), failedServers.get(), servers.get(currentServer.get()),
                        failedAttemptsForCurrentServer.get());

                currentServer.set((currentServer.get() + 1) % servers.size());
                failedAttemptsForCurrentServer.set(0);
            }
        }
    }

    @SuppressWarnings("checkstyle:superclone")
    @Override
    public Retryer clone() {
        // Not resetting currentServer so that the next connection is made through the current server.
        failedServers.set(0);
        failedAttemptsForCurrentServer.set(0);
        return this;
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public String name() {
        return FailoverFeignTarget.class.getSimpleName() + " instance with servers: " + servers;
    }

    @Override
    public String url() {
        return servers.get(currentServer.get());
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
                    // Call successful: set our attempts back to 0.
                    failedServers.set(0);
                    failedAttemptsForCurrentServer.set(0);
                }
                return response;
            }
        };
    }
}
