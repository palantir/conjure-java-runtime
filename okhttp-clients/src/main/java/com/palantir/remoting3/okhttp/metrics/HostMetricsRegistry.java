/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.okhttp.metrics;

import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public final class HostMetricsRegistry {
    private final LoadingCache<String, HostMetrics> hostMetrics;

    public HostMetricsRegistry(MetricRegistry registry, String serviceName) {
        this.hostMetrics = CacheBuilder.newBuilder()
                .maximumSize(1_000)
                .build(new CacheLoader<String, HostMetrics>() {
                    @Override
                    public HostMetrics load(String hostname) throws Exception {
                        return new HostMetrics(registry, serviceName, hostname);
                    }
                });
    }

    public void record(String hostname, int statusCode) {
        hostMetrics.getUnchecked(hostname).record(statusCode);
    }
}
