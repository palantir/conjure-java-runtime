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

package com.palantir.remoting2.config.ssl;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.immutables.value.Value;

/** A wrapper for {@link javax.net.ssl.SSLSocketFactory} and {@link javax.net.ssl.X509TrustManager}. */
@Value.Immutable
public abstract class TrustContext {
    @Value.Parameter
    public abstract SSLSocketFactory sslSocketFactory();

    @Value.Parameter
    public abstract X509TrustManager x509TrustManager();

    public static TrustContext of(SSLSocketFactory sslSocketFactory, X509TrustManager x509TrustManager) {
        return ImmutableTrustContext.builder()
                .sslSocketFactory(sslSocketFactory)
                .x509TrustManager(x509TrustManager)
                .build();
    }
}
