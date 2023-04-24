/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.config.ssl;

import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class SslUtils {
    private SslUtils() {}

    /**
     * Returns the first {@link TrustManager} initialized from the given configuration.
     * If created via an {@link SslConfiguration} this is always an {@link javax.net.ssl.X509TrustManager}.
     */
    public static X509TrustManager extractX509TrustManager(TrustManager[] trustManagers, SslConfiguration config) {
        TrustManager trustManager = trustManagers[0];
        if (trustManager instanceof X509TrustManager) {
            return (X509TrustManager) trustManager;
        } else {
            throw new RuntimeException(String.format(
                    "First TrustManager associated with SslConfiguration was expected to be a %s, but was a %s: %s",
                    X509TrustManager.class.getSimpleName(),
                    trustManager.getClass().getSimpleName(),
                    config.trustStorePath()));
        }
    }

    public static X509TrustManager extractX509TrustManager(TrustManager[] trustManagers) {
        TrustManager trustManager = trustManagers[0];
        if (trustManager instanceof X509TrustManager) {
            return (X509TrustManager) trustManager;
        } else {
            throw new RuntimeException(String.format(
                    "First TrustManager associated with SslConfiguration was expected to be a %s, but was a %s",
                    X509TrustManager.class.getSimpleName(),
                    trustManager.getClass().getSimpleName()));
        }
    }
}
