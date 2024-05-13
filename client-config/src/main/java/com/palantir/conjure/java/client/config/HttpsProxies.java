/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.config;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;

/**
 * Utility class for creating and identifying HTTPS proxy instances.
 * <p>
 * This class provides a way to create proxy instances that represent HTTPS connections, as there is no native support
 * for an HTTPS proxy type in the Java {@link Proxy} class. When using a proxy created with this class, consumers should
 * use the provided {@link #isHttps(Proxy)} method to determine if the proxy should be connected to using HTTPS.
 */
public final class HttpsProxies {

    public static Proxy create(InetSocketAddress address, boolean https) {
        if (https) {
            return new HttpsProxy(address);
        }
        return new Proxy(Proxy.Type.HTTP, address);
    }

    public static boolean isHttps(Proxy proxy) {
        return proxy instanceof HttpsProxy;
    }

    private HttpsProxies() {}

    private static final class HttpsProxy extends Proxy {
        HttpsProxy(SocketAddress sa) {
            super(Proxy.Type.HTTP, sa);
        }
    }
}
