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

package com.palantir.remoting.jaxrs;

import com.palantir.config.service.ServiceConfiguration;
import java.util.concurrent.TimeUnit;

/**
 * Static factory methods for producing creating HTTP proxies.
 */
public final class Client {

    private Client() {}

    /**
     * Creates a {@link T T client} for the given service configuration. The HTTP {@code User-Agent} header of every
     * request is set to the given non-empty {@code userAgent} string. Recommended user agents are of the form: {@code
     * ServiceName (Version)}, e.g. MyServer (1.2.3) For services that run multiple instances, recommended user agents
     * are of the form: {@code ServiceName/InstanceId (Version)}, e.g. MyServer/12 (1.2.3).
     */
    public static <T> T create(Class<T> serviceClass, String userAgent, ServiceConfiguration config) {
        ClientBuilder client = builder().ssl(config.security());
        if (config.connectTimeout().isPresent()) {
            client.connectTimeout(config.connectTimeout().get().toMilliseconds(), TimeUnit.MILLISECONDS);

        }
        if (config.readTimeout().isPresent()) {
            client.readTimeout(config.readTimeout().get().toMilliseconds(), TimeUnit.MILLISECONDS);
        }
        return client.build(serviceClass, userAgent, config.uris());
    }

    /**
     * Creates a builder for a client for a JAX-RS-specified service that attempts to connect to the given URIs with
     * round-robin fail-over.
     */
    public static ClientBuilder builder() {
        return new FeignJaxRsClientBuilder();
    }
}
