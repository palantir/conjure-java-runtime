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

package com.palantir.remoting2.jaxrs;

import com.google.common.reflect.Reflection;
import com.palantir.remoting2.clients.ClientBuilder;
import com.palantir.remoting2.clients.ClientConfig;
import com.palantir.remoting2.config.service.ServiceConfiguration;
import com.palantir.remoting2.ext.refresh.Refreshable;
import com.palantir.remoting2.ext.refresh.RefreshableProxyInvocationHandler;

/**
 * Static factory methods for producing creating JAX-RS HTTP proxies.
 */
public final class JaxRsClient {

    private JaxRsClient() {}

    /**
     * Creates a {@link T T client} for the given service configuration. The HTTP {@code User-Agent} header of every
     * request is set to the given non-empty {@code userAgent} string. Recommended user agents are of the form: {@code
     * ServiceName (Version)}, e.g. MyServer (1.2.3) For services that run multiple instances, recommended user agents
     * are of the form: {@code ServiceName/InstanceId (Version)}, e.g. MyServer/12 (1.2.3).
     */
    public static <T> T create(Class<T> serviceClass, String userAgent, ServiceConfiguration serviceConfig) {
        ClientConfig config = ClientConfig.fromServiceConfig(serviceConfig);
        return new FeignJaxRsClientBuilder(config).build(serviceClass, userAgent, serviceConfig.uris());
    }

    /**
     * Similar to {@link #create(Class, String, ServiceConfiguration)}, but creates a mutable client that updates its
     * configuration transparently whenever the given {@link Refreshable refreshable} {@link ServiceConfiguration}
     * changes.
     */
    public static <T> T create(
            Class<T> serviceClass,
            String userAgent,
            Refreshable<ServiceConfiguration> serviceConfig) {
        return Reflection.newProxy(serviceClass, RefreshableProxyInvocationHandler.create(
                serviceConfig,
                serviceConfiguration -> create(serviceClass, userAgent, serviceConfiguration)));
    }

    /**
     * Creates a builder for clients for a JAX-RS-specified service that attempts to connect to the given URIs with
     * round-robin fail-over.
     */
    public static ClientBuilder builder() {
        return new FeignJaxRsClientBuilder(ClientConfig.builder().build());
    }

    /**
     * Creates a builder for clients for a JAX-RS-specified service that attempts to connect to the given URIs with
     * round-robin fail-over, based on the given client configuration.
     */
    public static ClientBuilder builder(ClientConfig config) {
        return new FeignJaxRsClientBuilder(config);
    }
}
