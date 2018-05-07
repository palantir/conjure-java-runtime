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

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import java.time.Instant;

/**
 * Per-target-host HTTP response code metrics.
 */
public interface HostMetrics {

    /**
     * The name of the service these metrics describe. This is generally the simple name of the class being proxied (eg:
     * RemoteService).
     */
    String serviceName();

    /**
     * The name of the host these metrics describe. This may be the hostname, ip, or some other URI.
     */
    String hostname();

    /**
     * The url used to gather the metrics.
     */
    String url();

    /**
     * The {@link Instant} that these {@link HostMetrics} were last updated.
     */
    Instant lastUpdate();

    Timer get1xx();

    Timer get2xx();

    Timer get3xx();

    Timer get4xx();

    Timer get5xx();

    Timer getOther();

    Meter getIoExceptions();
}
