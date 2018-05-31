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

package com.palantir.remoting3.jaxrs;

import com.google.common.reflect.Reflection;
import com.palantir.remoting3.clients.ClientConfiguration;
import com.palantir.remoting3.clients.UserAgent;
import com.palantir.remoting3.clients.UserAgents;
import com.palantir.remoting3.ext.refresh.Refreshable;
import com.palantir.remoting3.ext.refresh.RefreshableProxyInvocationHandler;
import com.palantir.remoting3.okhttp.RpcMode;

/**
 * Static factory methods for producing creating JAX-RS HTTP proxies.
 */
public final class JaxRsClient {

    private JaxRsClient() {}

    /**
     * Creates a {@code T client} for the given service configuration. The HTTP {@code User-Agent} header of every
     * request is set to the given non-empty {@code userAgent} string.
     */
    public static <T> T create(Class<T> serviceClass, UserAgent userAgent, ClientConfiguration config, RpcMode mode) {
        // TODO(rfink): Add http-remoting agent as informational
        return create(serviceClass, UserAgents.format(userAgent), config, mode);
    }

    /**
     * Creates a {@code T client} for the given service configuration. The HTTP {@code User-Agent} header of every
     * request is set to the given non-empty {@code userAgent} string.
     */
    public static <T> T create(Class<T> serviceClass, UserAgent userAgent, ClientConfiguration config) {
        // TODO(rfink): Add http-remoting agent as informational
        return create(serviceClass, UserAgents.format(userAgent), config);
    }

    /**
     * Like {@link #create(Class, UserAgent, ClientConfiguration)}, but with a custom user agent string.
     *
     * @deprecated Use {@link #create(Class, UserAgent, ClientConfiguration)}
     */
    @Deprecated
    public static <T> T create(Class<T> serviceClass, String userAgent, ClientConfiguration config) {
        return new FeignJaxRsClientBuilder(config).build(serviceClass, userAgent);
    }

    /**
     * Like {@link #create(Class, String, ClientConfiguration)}, but with an RPC mode specified.
     */
    public static <T> T create(Class<T> serviceClass, String userAgent, ClientConfiguration config, RpcMode mode) {
        return new FeignJaxRsClientBuilder(config, mode).build(serviceClass, userAgent);
    }

    /**
     * Similar to {@link #create(Class, UserAgent, ClientConfiguration)}, but creates a mutable client that updates its
     * configuration transparently whenever the given {@link Refreshable refreshable} {@link ClientConfiguration}
     * changes.
     */
    public static <T> T create(
            Class<T> serviceClass,
            UserAgent userAgent,
            Refreshable<ClientConfiguration> config) {
        // TODO(rfink): Add http-remoting agent as informational
        return create(serviceClass, UserAgents.format(userAgent), config);
    }

    /**
     * Like {@link #create(Class, UserAgent, Refreshable)}, but with a custom user agent string.
     *
     * @deprecated Use {@link #create(Class, UserAgent, Refreshable)}
     */
    @Deprecated
    public static <T> T create(
            Class<T> serviceClass,
            String userAgent,
            Refreshable<ClientConfiguration> config) {
        return Reflection.newProxy(serviceClass, RefreshableProxyInvocationHandler.create(
                config,
                serviceConfiguration -> create(serviceClass, userAgent, serviceConfiguration)));
    }
}
