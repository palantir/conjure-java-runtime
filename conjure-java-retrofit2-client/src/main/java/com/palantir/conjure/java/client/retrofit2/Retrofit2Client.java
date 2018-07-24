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

package com.palantir.conjure.java.client.retrofit2;

import com.google.common.reflect.Reflection;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.ext.refresh.Refreshable;
import com.palantir.conjure.java.ext.refresh.RefreshableProxyInvocationHandler;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;

/**
 * Static factory methods for producing creating Retrofit2 HTTP proxies.
 */
public final class Retrofit2Client {

    private Retrofit2Client() {}

    /**
     * Creates a {@code T client} for the given service configuration. The HTTP {@code User-Agent} header of every
     * request is set to the given non-empty {@code userAgent} string.
     */
    public static <T> T create(
            Class<T> serviceClass,
            UserAgent userAgent,
            HostMetricsRegistry hostMetricsRegistry,
            ClientConfiguration config) {
        return new Retrofit2ClientBuilder(config)
                .hostMetricsRegistry(hostMetricsRegistry)
                .build(serviceClass, userAgent);
    }

    /**
     * Similar to {@link #create(Class, UserAgent, HostMetricsRegistry, ClientConfiguration)}, but creates a mutable
     * client that updates its configuration transparently whenever the given {@link Refreshable refreshable}
     * {@link ClientConfiguration} changes.
     */
    public static <T> T create(
            Class<T> serviceClass,
            UserAgent userAgent,
            HostMetricsRegistry hostMetricsRegistry,
            Refreshable<ClientConfiguration> config) {
        return Reflection.newProxy(serviceClass, RefreshableProxyInvocationHandler.create(
                config,
                serviceConfiguration -> create(serviceClass, userAgent, hostMetricsRegistry, serviceConfiguration)));
    }
}
