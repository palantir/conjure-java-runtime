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

import java.net.Proxy;
import java.net.SocketAddress;

public class HttpsProxy extends Proxy {
    /**
     * Creates an entry representing an HTTPS PROXY connection, this class is required due to a lack of support for
     * a native HTTPS proxy type.
     * Identical to {@link Proxy#Proxy(Type, SocketAddress)} with the type set to {@link Type#HTTP} but consumers should
     * check if the Proxy is an instanceof this class and connect to the proxy using HTTPS.
     * @param sa the {@code SocketAddress} for that proxy
     * @throws IllegalArgumentException when the type and the address are
     * incompatible
     */
    public HttpsProxy(SocketAddress sa) {
        super(Type.HTTP, sa);
    }
}
