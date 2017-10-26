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

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

public final class HostMetrics {

    private final Meter informational;
    private final Meter successful;
    private final Meter redirection;
    private final Meter clientError;
    private final Meter serverError;
    private final Meter other;

    public HostMetrics(MetricRegistry registry, String name, String hostname) {
        // TODO(jellis): #581 add hostname as a tag
        informational = registry.meter(MetricRegistry.name(name, "response", "family", "informational"));
        successful    = registry.meter(MetricRegistry.name(name, "response", "family", "successful"));
        redirection   = registry.meter(MetricRegistry.name(name, "response", "family", "redirection"));
        clientError   = registry.meter(MetricRegistry.name(name, "response", "family", "client-error"));
        serverError   = registry.meter(MetricRegistry.name(name, "response", "family", "server-error"));
        other         = registry.meter(MetricRegistry.name(name, "response", "family", "other"));
    }

    public void record(int statusCode) {
        switch (javax.ws.rs.core.Response.Status.Family.familyOf(statusCode)) {
            case INFORMATIONAL:
                informational.mark();
                break;
            case SUCCESSFUL:
                successful.mark();
                break;
            case REDIRECTION:
                redirection.mark();
                break;
            case CLIENT_ERROR:
                clientError.mark();
                break;
            case SERVER_ERROR:
                serverError.mark();
                break;
            case OTHER:
                other.mark();
                break;
        }
    }
}
