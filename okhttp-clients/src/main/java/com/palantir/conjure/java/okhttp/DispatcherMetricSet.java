/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Metric;
import com.google.common.collect.ImmutableMap;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricSet;
import java.util.Map;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

class DispatcherMetricSet implements TaggedMetricSet {

    private final ImmutableMap<MetricName, Metric> metrics;

    DispatcherMetricSet(Dispatcher dispatcher, ConnectionPool connectionPool) {
        TaggedMetricRegistry registry = new DefaultTaggedMetricRegistry();
        OkhttpMetrics okhttpMetrics = OkhttpMetrics.of(registry);
        okhttpMetrics.dispatcherCallsQueued(dispatcher::queuedCallsCount);
        okhttpMetrics.dispatcherCallsRunning(dispatcher::runningCallsCount);
        okhttpMetrics.connectionPoolConnectionsTotal(connectionPool::connectionCount);
        okhttpMetrics.connectionPoolConnectionsIdle(connectionPool::idleConnectionCount);
        this.metrics = ImmutableMap.copyOf(registry.getMetrics());
    }

    @Override
    public Map<MetricName, Metric> getMetrics() {
        return metrics;
    }
}
