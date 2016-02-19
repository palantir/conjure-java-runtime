/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.ssl;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.palantir.remoting.ssl.KeyStoreConfiguration;
import com.palantir.remoting.ssl.SslConfiguration;
import com.palantir.remoting.ssl.SslSocketFactories;
import com.palantir.remoting.ssl.TrustStoreConfiguration;
import java.io.IOException;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;

/**
 * Tests for {@link SslSocketFactories}.
 */
public final class SslSocketFactoriesTests {

    @Test
    public void testCreateSslSocketFactory_canCreateWithAllTrustStoreParams() {
        TrustStoreConfiguration trustStoreConfig = TrustStoreConfiguration
                .builder()
                .uri(TestConstants.CA_TRUST_STORE_PATH)
                .type(TestConstants.CA_TRUST_STORE_TYPE)
                .build();

        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(SslConfiguration
                .builder()
                .trust(trustStoreConfig)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithAllTrustStoreParamsPkcs12Format() {
        TrustStoreConfiguration trustStoreConfig = TrustStoreConfiguration
                .builder()
                .uri(TestConstants.SERVER_KEY_STORE_P12_PATH)
                .type(TestConstants.SERVER_KEY_STORE_P12_TYPE)
                .build();

        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trust(trustStoreConfig)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithOnlyTrustStorePath() {
        TrustStoreConfiguration trustStoreConfig = TrustStoreConfiguration
                .builder()
                .uri(TestConstants.CA_TRUST_STORE_PATH)
                .build();

        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trust(trustStoreConfig)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithAllKeyStoreParams() {
        TrustStoreConfiguration trustStoreConfig = TrustStoreConfiguration
                .builder()
                .uri(TestConstants.CA_TRUST_STORE_PATH)
                .build();

        KeyStoreConfiguration keyStoreConfig = KeyStoreConfiguration
                .builder()
                .uri(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .password(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .type(TestConstants.SERVER_KEY_STORE_JKS_TYPE)
                .build();

        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trust(trustStoreConfig)
                .key(keyStoreConfig)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithoutKeyStoreTypeJks() {
        TrustStoreConfiguration trustStoreConfig = TrustStoreConfiguration
                .builder()
                .uri(TestConstants.CA_TRUST_STORE_PATH)
                .build();

        KeyStoreConfiguration keyStoreConfig = KeyStoreConfiguration
                .builder()
                .uri(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .password(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .build();

        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trust(trustStoreConfig)
                .key(keyStoreConfig)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_jksKeyStoreTypeCannotBePkcs12Type() {
        try {
            TrustStoreConfiguration trustStoreConfig = TrustStoreConfiguration
                    .builder()
                    .uri(TestConstants.CA_TRUST_STORE_PATH)
                    .build();

            // bad configuration: key store is JKS format, but configuration specifies
            // that it is in PKCS12 format
            KeyStoreConfiguration keyStoreConfig = KeyStoreConfiguration
                    .builder()
                    .uri(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                    .password(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                    .type(TestConstants.SERVER_KEY_STORE_P12_TYPE)
                    .build();

            SslSocketFactories.createSslSocketFactory(
                    SslConfiguration.builder()
                    .trust(trustStoreConfig)
                    .key(keyStoreConfig)
                    .build());

            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(IOException.class)));
        }
    }

    @Test
    public void testCreateSslSocketFactory_keyStorePasswordMustBeCorrectJks() {
        try {
            TrustStoreConfiguration trustStoreConfig = TrustStoreConfiguration
                    .builder()
                    .uri(TestConstants.CA_TRUST_STORE_PATH)
                    .build();

            // bad configuration: keyStorePassword is incorrect
            KeyStoreConfiguration keyStoreConfig = KeyStoreConfiguration
                    .builder()
                    .uri(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                    .password("a")
                    .build();

            SslSocketFactories.createSslSocketFactory(
                    SslConfiguration.builder()
                    .trust(trustStoreConfig)
                    .key(keyStoreConfig)
                    .build());

            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(IOException.class)));
            assertThat(ex.getMessage(), containsString("Keystore was tampered with, or password was incorrect"));
        }
    }

    @Test
    public void testCreateSslSocketFactory_keyStorePasswordMustBeCorrectPkcs12() {
        try {
            TrustStoreConfiguration trustStoreConfig = TrustStoreConfiguration
                    .builder()
                    .uri(TestConstants.CA_TRUST_STORE_PATH)
                    .build();

            // bad configuration: keyStorePassword is incorrect
            KeyStoreConfiguration keyStoreConfig = KeyStoreConfiguration
                    .builder()
                    .uri(TestConstants.SERVER_KEY_STORE_P12_PATH)
                    .password("a")
                    .build();

            SslSocketFactories.createSslSocketFactory(
                    SslConfiguration.builder()
                    .trust(trustStoreConfig)
                    .key(keyStoreConfig)
                    .build());

            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(IOException.class)));
            assertThat(ex.getMessage(), containsString("keystore"));
        }
    }

    @Test
    public void testCreateSslSocketFactory_nonexistentKeyStoreAliasFails() {
        try {
            TrustStoreConfiguration trustStoreConfig = TrustStoreConfiguration
                    .builder()
                    .uri(TestConstants.CA_TRUST_STORE_PATH)
                    .build();

            // bad configuration: specified key alias does not exist in key store
            KeyStoreConfiguration keyStoreConfig = KeyStoreConfiguration
                    .builder()
                    .uri(TestConstants.MULTIPLE_KEY_STORE_JKS_PATH)
                    .password(TestConstants.MULTIPLE_KEY_STORE_JKS_PASSWORD)
                    .alias("nonexistent")
                    .build();

            SslSocketFactories.createSslSocketFactory(
                    SslConfiguration.builder()
                    .trust(trustStoreConfig)
                    .key(keyStoreConfig)
                    .build());

            fail();
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage(), containsString("Could not find key with alias"));
        }
    }

}
