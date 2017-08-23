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

package com.palantir.remoting3.config.ssl;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.palantir.remoting.api.config.ssl.SslConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class SslSocketFactoriesTests {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testCreateSslSocketFactory_withPemCertificatesByAlias() throws IOException {
        String cert = Files.toString(TestConstants.CA_PEM_CERT_PATH.toFile(), StandardCharsets.UTF_8);

        Map<String, PemX509Certificate> certs = ImmutableMap.of("cert", PemX509Certificate.of(cert));
        assertThat(SslSocketFactories.createSslSocketFactory(certs), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(certs), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithAllTrustStoreParams() {
        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .trustStoreType(TestConstants.CA_TRUST_STORE_TYPE)
                .build();

        assertThat(SslSocketFactories.createSslSocketFactory(sslConfig), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(sslConfig), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithAllTrustStoreParamsPkcs12Format() {
        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(TestConstants.SERVER_KEY_STORE_P12_PATH)
                .trustStoreType(TestConstants.SERVER_KEY_STORE_P12_TYPE)
                .build();

        assertThat(SslSocketFactories.createSslSocketFactory(sslConfig), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(sslConfig), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateTrustStorePemTypePemFormat() {
        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(TestConstants.CA_PEM_CERT_PATH)
                .trustStoreType(SslConfiguration.StoreType.PEM)
                .build();

        assertThat(SslSocketFactories.createSslSocketFactory(sslConfig), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(sslConfig), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateTrustStorePemTypeDerFormat() {
        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(TestConstants.CA_DER_CERT_PATH)
                .trustStoreType(SslConfiguration.StoreType.PEM)
                .build();

        assertThat(SslSocketFactories.createSslSocketFactory(sslConfig), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(sslConfig), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateTrustStorePemFromDirectory() throws IOException {
        File certFolder = tempFolder.newFolder();

        Files.copy(TestConstants.CA_DER_CERT_PATH.toFile(), certFolder.toPath().resolve("ca.der").toFile());
        Files.copy(TestConstants.SERVER_CERT_PEM_PATH.toFile(), certFolder.toPath().resolve("server.crt").toFile());
        Files.copy(TestConstants.CLIENT_CERT_PEM_PATH.toFile(), certFolder.toPath().resolve("client.crt").toFile());

        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(certFolder.toPath())
                .trustStoreType(SslConfiguration.StoreType.PEM)
                .build();

        assertThat(SslSocketFactories.createSslSocketFactory(sslConfig), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(sslConfig), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateTrustStorePuppetFromDirectory() throws IOException {
        File puppetFolder = tempFolder.newFolder();

        File certsFolder = puppetFolder.toPath().resolve("certs").toFile();
        assertThat(certsFolder.mkdir(), is(true));

        Files.copy(TestConstants.CA_PEM_CERT_PATH.toFile(), certsFolder.toPath().resolve("ca.pem").toFile());
        Files.copy(TestConstants.SERVER_CERT_PEM_PATH.toFile(), certsFolder.toPath().resolve("server.pem").toFile());
        Files.copy(TestConstants.CLIENT_CERT_PEM_PATH.toFile(), certsFolder.toPath().resolve("client.pem").toFile());

        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(puppetFolder.toPath())
                .trustStoreType(SslConfiguration.StoreType.PUPPET)
                .build();

        assertThat(SslSocketFactories.createSslSocketFactory(sslConfig), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(sslConfig), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateKeyStorePuppetFromDirectory() throws IOException {
        TestConstants.assumePkcs1ReaderExists();

        File puppetFolder = tempFolder.newFolder();

        File keysFolder = puppetFolder.toPath().resolve("private_keys").toFile();
        assertThat(keysFolder.mkdir(), is(true));

        File certsFolder = puppetFolder.toPath().resolve("certs").toFile();
        assertThat(certsFolder.mkdir(), is(true));

        Files.copy(TestConstants.SERVER_KEY_PEM_PATH.toFile(), keysFolder.toPath().resolve("server.pem").toFile());
        Files.copy(TestConstants.SERVER_CERT_PEM_PATH.toFile(), certsFolder.toPath().resolve("server.pem").toFile());

        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(puppetFolder.toPath())
                .keyStorePassword("")
                .keyStoreType(SslConfiguration.StoreType.PUPPET)
                .build();

        assertThat(SslSocketFactories.createSslSocketFactory(sslConfig), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(sslConfig), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateKeyStorePemType() {
        TestConstants.assumePkcs1ReaderExists();

        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_CERT_COMBINED_PEM_PATH)
                .keyStorePassword("")
                .keyStoreType(SslConfiguration.StoreType.PEM)
                .build();

        assertThat(SslSocketFactories.createSslSocketFactory(sslConfig), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(sslConfig), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithOnlyTrustStorePath() {
        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .build();

        assertThat(SslSocketFactories.createSslSocketFactory(sslConfig), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(sslConfig), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithAllKeyStoreParams() {
        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .keyStoreType(TestConstants.SERVER_KEY_STORE_JKS_TYPE)
                .build();

        assertThat(SslSocketFactories.createSslSocketFactory(sslConfig), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(sslConfig), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithoutKeyStoreTypeJks() {
        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .build();

        assertThat(SslSocketFactories.createSslSocketFactory(sslConfig), notNullValue());
        assertThat(SslSocketFactories.createX509TrustManager(sslConfig), notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_jksKeyStoreTypeCannotBePkcs12Type() {
        try {
            SslConfiguration sslConfig = SslConfiguration
                    .builder()
                    .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                    .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                    .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                    // bad configuration: key store is JKS format, but configuration specifies
                    // that it is in PKCS12 format
                    .keyStoreType(TestConstants.SERVER_KEY_STORE_P12_TYPE)
                    .build();

            SslSocketFactories.createSslSocketFactory(sslConfig);

            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(IOException.class)));
        }
    }

    @Test
    public void testCreateSslSocketFactory_keyStorePasswordMustBeCorrectJks() {
        try {
            SslConfiguration sslConfig = SslConfiguration
                    .builder()
                    .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                    .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                    // bad configuration: keyStorePassword is incorrect
                    .keyStorePassword("a")
                    .build();

            SslSocketFactories.createSslSocketFactory(sslConfig);

            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(IOException.class)));
            assertThat(ex.getMessage(), containsString("Keystore was tampered with, or password was incorrect"));
        }
    }

    @Test
    public void testCreateSslSocketFactory_keyStorePasswordMustBeCorrectPkcs12() {
        try {
            SslConfiguration sslConfig = SslConfiguration
                    .builder()
                    .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                    .keyStorePath(TestConstants.SERVER_KEY_STORE_P12_PATH)
                    // bad configuration: keyStorePassword is incorrect
                    .keyStorePassword("a")
                    .build();

            SslSocketFactories.createSslSocketFactory(sslConfig);

            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(IOException.class)));
            assertThat(ex.getMessage(), containsString("keystore"));
        }
    }

    @Test
    public void testCreateSslSocketFactory_nonexistentKeyStoreAliasFails() {
        try {
            SslConfiguration sslConfig = SslConfiguration
                    .builder()
                    .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                    .keyStorePath(TestConstants.MULTIPLE_KEY_STORE_JKS_PATH)
                    .keyStorePassword(TestConstants.MULTIPLE_KEY_STORE_JKS_PASSWORD)
                    // bad configuration: specified key alias does not exist in key store
                    .keyStoreKeyAlias("nonexistent")
                    .build();

            SslSocketFactories.createSslSocketFactory(sslConfig);

            fail();
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage(), containsString("Could not find key with alias"));
        }
    }

    @Test
    public void testCreateSslSocketFactory_keystorePasswordRequiredIfUriPresent() {
        try {
            SslConfiguration sslConfig = SslConfiguration
                    .builder()
                    .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                    .keyStorePath(TestConstants.MULTIPLE_KEY_STORE_JKS_PATH)
                    .build();

            SslSocketFactories.createSslSocketFactory(sslConfig);

            fail();
        } catch (IllegalArgumentException ex) {
            assertThat(
                    ex.getMessage(),
                    containsString("keyStorePath and keyStorePassword must both be present or both be absent"));
        }
    }

    @Test
    public void testCreateSslSocketFactory_keyStorePathRequiredIfPasswordPresent() {
        try {
            SslConfiguration sslConfig = SslConfiguration
                    .builder()
                    .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                    .keyStorePassword(TestConstants.MULTIPLE_KEY_STORE_JKS_PASSWORD)
                    .build();

            SslSocketFactories.createSslSocketFactory(sslConfig);

            fail();
        } catch (IllegalArgumentException ex) {
            assertThat(
                    ex.getMessage(),
                    containsString("keyStorePath and keyStorePassword must both be present or both be absent"));
        }
    }

    @Test
    public void testCreateSslSocketFactory_keyStorePathRequiredIfAliasPresent() {
        try {
            SslConfiguration sslConfig = SslConfiguration
                    .builder()
                    .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                    .keyStoreKeyAlias(TestConstants.MULTIPLE_KEY_STORE_CLIENT_ALIAS)
                    .build();

            SslSocketFactories.createSslSocketFactory(sslConfig);

            fail();
        } catch (IllegalArgumentException ex) {
            assertThat(ex.getMessage(), containsString("keyStorePath must be present if keyStoreKeyAlias is present"));
        }
    }

    @Test
    public void testCreateSslSocketFactory_failsWithInvalidPath() {
        try {
            SslConfiguration sslConfig = SslConfiguration
                    .builder()
                    .trustStorePath(new File("foo/bar").toPath())
                    .build();

            SslSocketFactories.createSslSocketFactory(sslConfig);

            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), instanceOf(NoSuchFileException.class));
            assertThat(ex.getMessage(), containsString("foo/bar"));
        }
    }

    @Test
    public void testCreateSslSocketFactory_supportsRelativePath() {
        SslConfiguration sslConfig = SslConfiguration
                .builder()
                .trustStorePath(new File("src/test/resources/testCA/testCA.jks").toPath())
                .build();

        SslSocketFactories.createSslSocketFactory(sslConfig);
    }

}
