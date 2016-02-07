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

import java.io.IOException;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;

/**
 * Tests for {@link SslSocketFactories}.
 */
public final class SslSocketFactoriesTests {

    @Test
    public void testCreateSslSocketFactory_canCreateWithAllTrustStoreParams() {
        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .trustStoreType(TestConstants.CA_TRUST_STORE_TYPE)
                .trustStorePassword(TestConstants.CA_TRUST_STORE_PASSWORD)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithAllTrustStoreParamsPkcs12Format() {
        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trustStorePath(TestConstants.SERVER_KEY_STORE_P12_PATH)
                .trustStoreType(TestConstants.SERVER_KEY_STORE_P12_TYPE)
                .trustStorePassword(TestConstants.SERVER_KEY_STORE_P12_PASSWORD)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithoutTrustStorePassword() {
        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .trustStoreType(TestConstants.CA_TRUST_STORE_TYPE)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithoutTrustStoreType() {
        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .trustStorePassword(TestConstants.CA_TRUST_STORE_PASSWORD)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithOnlyTrustStorePath() {
        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithAllKeyStoreParams() {
        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .keyStoreType(TestConstants.SERVER_KEY_STORE_JKS_TYPE)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithoutKeyStoreTypeJks() {
        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithoutKeyStoreTypePkcs12() {
        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_P12_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_P12_PASSWORD)
                .build());

        assertThat(factory, notNullValue());
    }


    @Test
    public void testCreateSslSocketFactory_jksKeyStoreTypeCannotBePkcs12Type() {
        try {
            // bad configuration: key store is JKS format, but configuration specifies
            // that it is in PKCS12 format
            SslSocketFactories.createSslSocketFactory(
                    SslConfiguration.builder()
                            .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                            .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                            .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                            .keyStoreType(TestConstants.SERVER_KEY_STORE_P12_TYPE)
                            .build());
            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(IOException.class)));
        }
    }

    @Test
    public void testCreateSslSocketFactory_keyStorePasswordRequired() {
        try {
            // bad configuration: keyStorePassword must be specified if
            // keyStorePath is present
            SslSocketFactories.createSslSocketFactory(
                    SslConfiguration.builder()
                    .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                    .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                    .build());
            fail();
        } catch (IllegalArgumentException ex) {
            assertThat(ex.getMessage(), containsString("keyStorePassword cannot be absent"));
        }
    }

    @Test
    public void testCreateSslSocketFactory_keyStorePasswordMustBeCorrectJks() {
        try {
            // bad configuration: keyStorePassword is incorrect
            SslSocketFactories.createSslSocketFactory(
                    SslConfiguration.builder()
                    .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                    .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                    .keyStorePassword("")
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
            // bad configuration: keyStorePassword is incorrect
            SslSocketFactories.createSslSocketFactory(
                    SslConfiguration.builder()
                    .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                    .keyStorePath(TestConstants.SERVER_KEY_STORE_P12_PATH)
                    .keyStorePassword("")
                    .build());
            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(IOException.class)));
            assertThat(ex.getMessage(), containsString("keystore password was incorrect"));
        }
    }

}
