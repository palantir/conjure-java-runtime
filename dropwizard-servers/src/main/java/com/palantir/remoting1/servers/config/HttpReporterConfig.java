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

package com.palantir.remoting1.servers.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.github.kristofa.brave.http.HttpSpanCollector;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Configuration options for Reporter. */
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
@JsonDeserialize(as = ImmutableHttpReporterConfig.class)
@JsonSerialize(as = ImmutableHttpReporterConfig.class)
@SuppressWarnings("checkstyle:designforextension")
public abstract class HttpReporterConfig implements ReporterTypeConfig {
    private static final Logger log = LoggerFactory.getLogger(HttpReporterConfig.class);

    @Value.Default
    public String type() {
        return "http";
    }

    @Value.Parameter
    protected abstract String url();

    @JsonIgnore
    @Value.Default
    protected SpanCollectorMetricsHandler metrics() {
        return new SpanCollectorMetricsHandler() {
            @Override
            public void incrementAcceptedSpans(int quantity) {
                if (log.isTraceEnabled()) {
                    log.trace("Accepted {} trace spans", quantity);
                }
            }

            @Override
            public void incrementDroppedSpans(int quantity) {
                if (log.isInfoEnabled()) {
                    log.info("Dropped {} trace spans", quantity);
                }
            }
        };
    }

    @Override
    @JsonIgnore
    public final SpanCollector reporter() {
        return HttpSpanCollector.create(url(), metrics());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends ImmutableHttpReporterConfig.Builder {}
}
