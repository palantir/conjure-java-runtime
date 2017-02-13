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

package com.palantir.remoting2.clients;

import java.util.Arrays;
import java.util.List;

/** Abstraction for creating HTTP-invoking dynamic proxies around service interfaces. */
public abstract class ClientBuilder {

    /**
     * Creates and returns a dynamic proxy of the given {@code serviceClass} type against the given URIs. The user agent
     * string is embedded as the HTTP {@code User-Agent} header for all requests. Recommended user agents are of the
     * form: {@code ServiceName (Version)}, e.g. MyServer (1.2.3) For services that run multiple instances, recommended
     * user agents are of the form: {@code ServiceName/InstanceId (Version)}, e.g. MyServer/12 (1.2.3).
     */
    public abstract <T> T build(Class<T> serviceClass, String userAgent, List<String> uris);

    /** See {@link #build}. */
    public final <T> T build(Class<T> serviceClass, String userAgent, String... uris) {
        return build(serviceClass, userAgent, Arrays.asList(uris));
    }
}
