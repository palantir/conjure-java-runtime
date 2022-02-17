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

package com.palantir.conjure.java.client.config;

import com.google.common.collect.ImmutableList;

public final class CipherSuites {

    private static final ImmutableList<String> OTHER_CIPHER_SUITES = ImmutableList.of(
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256");

    private static final ImmutableList<String> GCM_CIPHER_SUITES = ImmutableList.of(
            "TLS_AES_256_GCM_SHA384",
            "TLS_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256");

    private static final ImmutableList<String> ALL_CIPHER_SUITES = ImmutableList.<String>builder()
            .addAll(GCM_CIPHER_SUITES)
            .addAll(OTHER_CIPHER_SUITES)
            .build();

    /**
     * This should not be used.
     *
     * The Java 8 implementation of GCM ciphers was much slower than CBC suites, however in all newer releases
     * the GCM ciphers out-perform CBC (and all other suites).
     *
     * @deprecated No longer necessary, GCM ciphers provide the most throughput on modern JVMs.
     */
    @Deprecated
    public static String[] fastCipherSuites() {
        return allCipherSuites();
    }

    /** Known safe GCM cipher suites. */
    public static String[] gcmCipherSuites() {
        return GCM_CIPHER_SUITES.toArray(new String[0]);
    }

    /** Union of {@link #fastCipherSuites()} and {@link #gcmCipherSuites()}. */
    public static String[] allCipherSuites() {
        return ALL_CIPHER_SUITES.toArray(new String[0]);
    }

    private CipherSuites() {}
}
