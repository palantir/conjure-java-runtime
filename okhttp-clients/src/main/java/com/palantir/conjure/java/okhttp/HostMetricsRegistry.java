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

package com.palantir.conjure.java.okhttp;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Suppliers;
import com.palantir.conjure.java.client.config.ImmutablesStyle;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HostMetricsRegistry implements HostEventsSink {

    private static final Logger log = LoggerFactory.getLogger(HostMetricsRegistry.class);

    private final LoadingCache<ServiceHostAndPort, DefaultHostMetrics> hostMetrics;

    public HostMetricsRegistry() {
        this.hostMetrics = Caffeine.newBuilder()
                .maximumSize(1_000)
                .initialCapacity(64)
                .expireAfterAccess(Duration.ofDays(1))
                .build(key -> new DefaultHostMetrics(key.serviceName(), key.hostname(), key.port(), Clock.systemUTC()));
    }

    @Override
    public void record(String serviceName, String hostname, int port, int statusCode, long micros) {
        try {
            hostMetrics
                    .get(ImmutableServiceHostAndPort.of(serviceName, hostname, port))
                    .record(statusCode, micros);
        } catch (Exception e) {
            log.warn(
                    "Unable to record metrics for host and port",
                    UnsafeArg.of("hostname", hostname),
                    SafeArg.of("port", port),
                    e);
        }
    }

    @Override
    public void recordIoException(String serviceName, String hostname, int port) {
        try {
            hostMetrics
                    .get(ImmutableServiceHostAndPort.of(serviceName, hostname, port))
                    .recordIoException();
        } catch (Exception e) {
            log.warn(
                    "Unable to record IO exception for host and port",
                    UnsafeArg.of("hostname", hostname),
                    SafeArg.of("port", port),
                    e);
        }
    }

    @Override
    public HostEventCallback callback(String serviceName, String hostname, int port) {
        return new HostEventRegistryCallback(serviceName, hostname, port);
    }

    public Collection<HostMetrics> getMetrics() {
        return Collections.unmodifiableCollection(hostMetrics.asMap().values());
    }

    @Value.Immutable
    @ImmutablesStyle
    interface ServiceHostAndPort {
        @Value.Parameter
        String serviceName();

        @Value.Parameter
        String hostname();

        @Value.Parameter
        int port();
    }

    private final class HostEventRegistryCallback implements HostEventCallback {

        private final ServiceHostAndPort target;
        private final Supplier<DefaultHostMetrics> hostMetricsSupplier;

        HostEventRegistryCallback(String serviceName, String hostname, int port) {
            this.target = ImmutableServiceHostAndPort.of(serviceName, hostname, port);
            // recomputed each hour, must be shorter than the 1 day timeout used by the cache.
            // This avoids an unnecessary cache load to record metrics.
            this.hostMetricsSupplier =
                    Suppliers.memoizeWithExpiration(() -> hostMetrics.get(target), 1, TimeUnit.HOURS);
        }

        @Override
        public void record(int statusCode, long micros) {
            try {
                hostMetricsSupplier.get().record(statusCode, micros);
            } catch (Exception e) {
                log.warn(
                        "Unable to record metrics for host and port",
                        UnsafeArg.of("hostname", target.hostname()),
                        SafeArg.of("port", target.port()),
                        e);
            }
        }

        @Override
        public void recordIoException() {
            try {
                hostMetricsSupplier.get().recordIoException();
            } catch (Exception e) {
                log.warn(
                        "Unable to record IO exception for host and port",
                        UnsafeArg.of("hostname", target.hostname()),
                        SafeArg.of("port", target.port()),
                        e);
            }
        }

        @Override
        public String toString() {
            return "HostEventRegistryCallback{target=" + target + '}';
        }
    }
}
