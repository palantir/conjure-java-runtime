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

    private static final ImmutableList<String> FAST_CIPHER_SUITES = ImmutableList.of(
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_EMPTY_RENEGOTIATION_INFO_SCSV");

    private static final ImmutableList<String> GCM_CIPHER_SUITES = ImmutableList.of(
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_GCM_SHA256");

    private static final ImmutableList<String> ALL_CIPHER_SUITES =
            ImmutableList.<String>builder().addAll(FAST_CIPHER_SUITES).addAll(GCM_CIPHER_SUITES).build();

    /**
     * Known fast and safe cipher suites on the JVM.
     *
     * <p>In an ideal world, we'd use GCM suites, but they're an order of magnitude slower than the CBC suites, which
     * have JVM optimizations already. We should revisit with JDK9.
     *
     * <p>See also:
     *
     * <ul>
     *   <li>http://openjdk.java.net/jeps/246
     *   <li>https://bugs.openjdk.java.net/secure/attachment/25422/GCM%20Analysis.pdf
     * </ul>
     */
    public static String[] fastCipherSuites() {
        return FAST_CIPHER_SUITES.toArray(new String[0]);
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
