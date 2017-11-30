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

package com.palantir.remoting3.okhttp;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.remoting3.clients.ImmutablesStyle;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class HostMetricsRegistry {

    private static final Logger log = LoggerFactory.getLogger(HostMetricsRegistry.class);

    private final LoadingCache<ServiceAndHost, DefaultHostMetrics> hostMetrics;

    HostMetricsRegistry() {
        this.hostMetrics = CacheBuilder.newBuilder()
                .maximumSize(1_000)
                .expireAfterAccess(1, TimeUnit.DAYS)
                .build(new CacheLoader<ServiceAndHost, DefaultHostMetrics>() {
                    @Override
                    public DefaultHostMetrics load(ServiceAndHost key) throws Exception {
                        return new DefaultHostMetrics(key.serviceName(), key.hostname());
                    }
                });
    }

    void record(String serviceName, String hostname, int statusCode, long micros) {
        try {
            hostMetrics.getUnchecked(ImmutableServiceAndHost.of(serviceName, hostname)).record(statusCode, micros);
        } catch (Exception e) {
            log.warn("Unable to record metrics for host", UnsafeArg.of("hostname", hostname));
        }
    }

    Collection<HostMetrics> getMetrics() {
        return Collections.unmodifiableCollection(hostMetrics.asMap().values());
    }

    @Value.Immutable
    @ImmutablesStyle
    interface ServiceAndHost {
        @Value.Parameter String serviceName();
        @Value.Parameter String hostname();
    }
}
