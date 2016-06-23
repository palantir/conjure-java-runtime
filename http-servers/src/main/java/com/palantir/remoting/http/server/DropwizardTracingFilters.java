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

package com.palantir.remoting.http.server;

import com.github.kristofa.brave.LoggingSpanCollector;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.jaxrs2.BraveContainerRequestFilter;
import com.github.kristofa.brave.jaxrs2.BraveContainerResponseFilter;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.net.InetAddresses;
import io.dropwizard.Configuration;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/** Static utilities for registering Brave/Zipkin filters with Dropwizard applications. */
public final class DropwizardTracingFilters {

    private DropwizardTracingFilters() {}

    /**
     * Registers Brave request&response filters for logging Zipkin-style tracing information. The Zipkin logger carries
     * the given tracerName and logs server IP/Port information extracted from the given Dropwizard configuration with
     * "best effort".
     * <p>
     * TODO(rfink) Is there a more stable way to retrieve IP/Port information?
     */
    public static void registerTracers(JerseyEnvironment environment, Configuration config, String tracerName) {
        ServerTracer serverTracer = getServerTracer(extractIp(config), extractPort(config), tracerName);
        environment.register(new BraveContainerRequestFilter(
                new ServerRequestInterceptor(serverTracer),
                new DefaultSpanNameProvider()
        ));
        environment.register(new BraveContainerResponseFilter(
                new ServerResponseInterceptor(serverTracer)
        ));
    }

    private static ServerTracer getServerTracer(int ip, int port, String name) {
        return ServerTracer.builder()
                .traceSampler(Sampler.ALWAYS_SAMPLE)
                .randomGenerator(new Random())
                .state(new ThreadLocalServerClientAndLocalSpanState(ip, port, name))
                .spanCollector(new LoggingSpanCollector("ServerTracer(" + name + ")"))
                .build();
    }

    private static int extractIp(Configuration config) {
        String bindHost = extractConnector(config, 0)
                .transform(new Function<ConnectorFactory, String>() {
                    @Override
                    public String apply(ConnectorFactory factory) {
                        if (factory instanceof HttpsConnectorFactory) {
                            return Strings.nullToEmpty(((HttpsConnectorFactory) factory).getBindHost());
                        } else if (factory instanceof HttpConnectorFactory) {
                            return Strings.nullToEmpty(((HttpConnectorFactory) factory).getBindHost());
                        } else {
                            return "";
                        }
                    }
                })
                .or("");

        if (!bindHost.isEmpty()) {
            try {
                return InetAddresses.coerceToInteger(InetAddress.getByName(bindHost));
            } catch (UnknownHostException e) {
                return 0;
            }
        } else {
            return 0;
        }
    }

    private static int extractPort(Configuration config) {
        return extractConnector(config, 0)
                .transform(new Function<ConnectorFactory, Integer>() {
                    @Override
                    public Integer apply(ConnectorFactory factory) {
                        if (factory instanceof HttpsConnectorFactory) {
                            return ((HttpsConnectorFactory) factory).getPort();
                        } else if (factory instanceof HttpConnectorFactory) {
                            return ((HttpConnectorFactory) factory).getPort();
                        } else {
                            return -1;
                        }
                    }
                })
                .or(-1);
    }

    private static Optional<ConnectorFactory> extractConnector(Configuration config, int num) {
        try {
            return Optional.of(((DefaultServerFactory) config.getServerFactory()).getApplicationConnectors().get(num));
        } catch (RuntimeException e) {
            return Optional.absent();
        }
    }
}
