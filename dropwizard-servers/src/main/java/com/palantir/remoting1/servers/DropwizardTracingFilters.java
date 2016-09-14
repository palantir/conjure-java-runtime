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

package com.palantir.remoting1.servers;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.InheritableServerClientAndLocalSpanState;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.ServerClientAndLocalSpanState;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.ext.SlfLoggingSpanCollector;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.jaxrs2.BraveContainerRequestFilter;
import com.github.kristofa.brave.jaxrs2.BraveContainerResponseFilter;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.net.InetAddresses;
import com.twitter.zipkin.gen.Endpoint;
import io.dropwizard.Configuration;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.setup.Environment;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Static utilities for registering Brave/Zipkin filters with Dropwizard applications. */
final class DropwizardTracingFilters {
    private static final Logger logger = LoggerFactory.getLogger(DropwizardTracingFilters.class);

    private DropwizardTracingFilters() {}

    /**
     * Registers Brave request&response filters for logging Zipkin-style tracing information, as well as for augmenting
     * the SFL4J {@link org.slf4j.MDC} with with a {@link TraceIdLoggingFilter#MDC_KEY trace id field}. The Zipkin log
     * collector carries the given tracerName and logs server IP/Port information extracted from the given Dropwizard
     * configuration with "best effort".
     * <p>
     * TODO(rfink) Is there a more stable way to retrieve IP/Port information?
     */
    static void registerTracers(Environment environment, Configuration config, String tracerName) {
        final BraveTracer tracer = getOrCreateBraveTracer(config, tracerName);
        environment.jersey().register(new BraveContainerRequestFilter(
                new ServerRequestInterceptor(tracer.getBrave().serverTracer()),
                new DefaultSpanNameProvider()
        ));
        environment.jersey().register(new BraveContainerResponseFilter(
                new ServerResponseInterceptor(tracer.getBrave().serverTracer())
        ));
        environment.servlets()
                .addFilter(TraceIdLoggingFilter.class.getSimpleName(), TraceIdLoggingFilter.INSTANCE)
                .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        Tracers.setActiveTracer(tracer);
    }


    private static BraveTracer getOrCreateBraveTracer(Configuration config, String name) {
        Tracer activeTracer = Tracers.activeTracer();
        return (activeTracer instanceof BraveTracer)
                ? (BraveTracer) activeTracer
                : createBraveTracer(config, name);
    }

    private static BraveTracer createBraveTracer(Configuration config, String name) {
        int ip = extractIp(config);
        int port = extractPort(config);
        ServerClientAndLocalSpanState state = new InheritableServerClientAndLocalSpanState(
                Endpoint.create(name, ip, port));
        // TODO (davids) make the sampler and collectors configurable
        Sampler sampler = Sampler.ALWAYS_SAMPLE;
        final String loggerName = "tracing." + name;
        SpanCollector spanCollector = new SlfLoggingSpanCollector(loggerName);

        logger.info("Starting tracer for {} writing to logger {}", name, loggerName);
        Brave brave = new Brave.Builder(state)
                .traceSampler(sampler)
                .spanCollector(spanCollector)
                .clock(BraveTracer.defaultClock())
                .build();
        return new BraveTracer(brave);
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

        if (bindHost.isEmpty()) {
            return 0;
        } else {
            try {
                return InetAddresses.coerceToInteger(InetAddress.getByName(bindHost));
            } catch (UnknownHostException e) {
                return 0;
            }
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
