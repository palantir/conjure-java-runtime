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

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.google.common.reflect.Reflection;
import com.palantir.remoting2.clients.ClientBuilder;
import com.palantir.remoting2.clients.ClientConfig;
import com.palantir.remoting2.config.service.ProxyConfiguration;
import com.palantir.remoting2.config.service.ProxyConfiguration.Type;
import com.palantir.remoting2.config.service.ServiceConfiguration;
import com.palantir.remoting2.config.ssl.SslSocketFactories;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import com.palantir.remoting2.ext.refresh.Refreshable;
import com.palantir.remoting2.ext.refresh.RefreshableProxyInvocationHandler;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClientBuilder;

/**
 * Static factory methods for producing creating JAX-RS HTTP proxies.
 */
public final class JaxRsClient {

    private JaxRsClient() {}

    /**
     * Creates a {@code T client} for the given service configuration. The HTTP {@code User-Agent} header of every
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

    public static javax.ws.rs.client.ClientBuilder jaxrsBuilder(ServiceConfiguration serviceConfig) {
        ClientConfig config = ClientConfig.fromServiceConfig(serviceConfig);
        JerseyClientBuilder clientBuilder = new JerseyClientBuilder();
        clientBuilder.register(new JacksonJaxbJsonProvider(ObjectMappers.newClientObjectMapper(), JacksonJaxbJsonProvider.BASIC_ANNOTATIONS));
        clientBuilder.property(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, Boolean.TRUE);

        if (serviceConfig.security().isPresent()) {
            clientBuilder.sslContext(SslSocketFactories.createSslContext(serviceConfig.security().get()));
        }
        clientBuilder.property(ClientProperties.CONNECT_TIMEOUT, config.connectTimeout().toMilliseconds());
        clientBuilder.property(ClientProperties.READ_TIMEOUT, config.writeTimeout().toMilliseconds());
        if (config.proxy().isPresent() && config.proxy().get().type() == Type.HTTP) {
            ProxyConfiguration proxy = config.proxy().get();
            clientBuilder.property(ClientProperties.PROXY_URI, proxy.maybeHostAndPort().get());
            if (proxy.credentials().isPresent()) {
                clientBuilder.property(ClientProperties.PROXY_USERNAME, proxy.credentials().get().username());
                clientBuilder.property(ClientProperties.PROXY_PASSWORD, proxy.credentials().get().password());
            }
        }
        return clientBuilder;
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
