/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.ssl;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test constants for trust stores and key stores used in unit tests.
 */
final class TestConstants {

    static final URI CA_TRUST_STORE_PATH =
            Paths.get("src", "test", "resources", "testCA", "testCATrustStore.jks").toUri();
    static final String CA_TRUST_STORE_TYPE = "JKS";
    static final String CA_TRUST_STORE_PASSWORD = "testCA";

    static final Path CA_CRL_PATH = Paths.get("src", "test", "resources", "crl.pem");

    static final URI SERVER_KEY_STORE_JKS_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testServer",
            "testServerKeyStore.jks").toUri();
    static final String SERVER_KEY_STORE_JKS_TYPE = "JKS";
    static final String SERVER_KEY_STORE_JKS_PASSWORD = "serverStore";

    static final URI SERVER_KEY_STORE_P12_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testServer",
            "testServerKeyStore.p12").toUri();
    static final String SERVER_KEY_STORE_P12_TYPE = "PKCS12";
    static final String SERVER_KEY_STORE_P12_PASSWORD = "testServer";

    static final URI CLIENT_KEY_STORE_JKS_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testClient",
            "testClientKeyStore.jks").toUri();
    static final String CLIENT_KEY_STORE_JKS_TYPE = "JKS";
    static final String CLIENT_KEY_STORE_JKS_PASSWORD = "clientStore";

    static final URI CLIENT_KEY_STORE_P12_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testClient",
            "testClientKeyStore.p12").toUri();
    static final String CLIENT_KEY_STORE_P12_TYPE = "PKCS12";
    static final String CLIENT_KEY_STORE_P12_PASSWORD = "testClient";

    static final URI MULTIPLE_KEY_STORE_JKS_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "multiple.jks").toUri();
    static final String MULTIPLE_KEY_STORE_JKS_PASSWORD = "multiple";
    static final String MULTIPLE_KEY_STORE_CLIENT_ALIAS = "testClient";
    static final String MULTIPLE_KEY_STORE_SERVER_ALIAS = "testServer";

    private TestConstants() {}

}
