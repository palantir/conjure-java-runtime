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

package com.palantir.remoting3.retrofit2;

import com.google.common.reflect.Reflection;
import com.palantir.remoting3.clients.ClientConfiguration;
import com.palantir.remoting3.ext.refresh.Refreshable;
import com.palantir.remoting3.ext.refresh.RefreshableProxyInvocationHandler;

/**
 * Static factory methods for producing creating Retrofit2 HTTP proxies.
 */
public final class Retrofit2Client {

    private Retrofit2Client() {}

    /**
     * Creates a {@code T client} for the given service configuration. The HTTP {@code User-Agent} header of every
     * request is set to the given non-empty {@code userAgent} string. Recommended user agents are of the form: {@code
     * ServiceName (Version)}, e.g. MyServer (1.2.3) For services that run multiple instances, recommended user agents
     * are of the form: {@code ServiceName/InstanceId (Version)}, e.g. MyServer/12 (1.2.3).
     */
    public static <T> T create(Class<T> serviceClass, String userAgent, ClientConfiguration config) {
        return new Retrofit2ClientBuilder(config).build(serviceClass, userAgent);
    }

    /**
     * Similar to {@link #create(Class, String, ClientConfiguration)}, but creates a mutable client that updates its
     * configuration transparently whenever the given {@link Refreshable refreshable} {@link ClientConfiguration}
     * changes.
     */
    public static <T> T create(
            Class<T> serviceClass,
            String userAgent,
            Refreshable<ClientConfiguration> config) {
        return Reflection.newProxy(serviceClass, RefreshableProxyInvocationHandler.create(
                config,
                serviceConfiguration -> create(serviceClass, userAgent, serviceConfiguration)));
    }
}
