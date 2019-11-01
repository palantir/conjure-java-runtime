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

package com.palantir.conjure.java.config.ssl;

import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.config.ssl.pkcs1.Pkcs1Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ServiceLoader;
import org.junit.Assume;

/** Test constants for trust stores and key stores used in unit tests. */
final class TestConstants {

    static final Path CA_TRUST_STORE_PATH = Paths.get("src", "test", "resources", "testCA", "testCA.jks");
    static final Path CA_DER_CERT_PATH = Paths.get("src", "test", "resources", "testCA", "testCA.der");
    static final Path CA_PEM_CERT_PATH = Paths.get("src", "test", "resources", "testCA", "testCA.cer");
    static final SslConfiguration.StoreType CA_TRUST_STORE_TYPE = SslConfiguration.StoreType.JKS;
    static final String CA_TRUST_STORE_JKS_PASSWORD = "caStore";

    static final Path SERVER_KEY_STORE_JKS_PATH = Paths.get("src", "test", "resources", "testServer", "testServer.jks");
    static final SslConfiguration.StoreType SERVER_KEY_STORE_JKS_TYPE = SslConfiguration.StoreType.JKS;
    static final String SERVER_KEY_STORE_JKS_PASSWORD = "serverStore";

    static final Path SERVER_KEY_STORE_P12_PATH = Paths.get("src", "test", "resources", "testServer", "testServer.p12");
    static final SslConfiguration.StoreType SERVER_KEY_STORE_P12_TYPE = SslConfiguration.StoreType.PKCS12;
    static final String SERVER_KEY_STORE_P12_PASSWORD = "testServer";

    static final Path SERVER_KEY_PEM_PATH = Paths.get("src", "test", "resources", "testServer", "testServer.key");
    static final Path SERVER_CERT_PEM_PATH = Paths.get("src", "test", "resources", "testServer", "testServer.cer");
    static final Path SERVER_KEY_CERT_COMBINED_PEM_PATH =
            Paths.get("src", "test", "resources", "testServer", "testServer.pem");

    static final Path CLIENT_KEY_STORE_JKS_PATH = Paths.get("src", "test", "resources", "testClient", "testClient.jks");
    static final String CLIENT_KEY_STORE_JKS_PASSWORD = "clientStore";

    static final Path CLIENT_KEY_STORE_P12_PATH = Paths.get("src", "test", "resources", "testClient", "testClient.p12");
    static final SslConfiguration.StoreType CLIENT_KEY_STORE_P12_TYPE = SslConfiguration.StoreType.PKCS12;
    static final String CLIENT_KEY_STORE_P12_PASSWORD = "testClient";

    static final Path CLIENT_CERT_PEM_PATH = Paths.get("src", "test", "resources", "testClient", "testClient.cer");
    static final Path CLIENT_KEY_CERT_COMBINED_PEM_PATH =
            Paths.get("src", "test", "resources", "testClient", "testClient.pem");

    static final Path MULTIPLE_KEY_STORE_JKS_PATH = Paths.get("src", "test", "resources", "multiple.jks");
    static final String MULTIPLE_KEY_STORE_JKS_PASSWORD = "multiple";
    static final String MULTIPLE_KEY_STORE_CLIENT_ALIAS = "testClient";
    static final String MULTIPLE_KEY_STORE_SERVER_ALIAS = "testServer";

    static final Path CHILD_KEY_CERT_CHAIN_PEM_PATH =
            Paths.get("src", "test", "resources", "testChild", "testChild_key_cert_chain.pem");
    static final Path COMBINED_CRL_PATH = Paths.get("src", "test", "resources", "crl", "combined.crl");

    static void assumePkcs1ReaderExists() {
        Assume.assumeTrue(
                "Test requires at least one Pkcs1Reader service to be present",
                ServiceLoader.load(Pkcs1Reader.class).iterator().hasNext());
    }

    private TestConstants() {}
}
