/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.trust;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;

public final class TrustStoreTests {

    private static final String TRUST_STORE_PASSWORD = "testtest";
    private static final Path TRUST_STORE_PATH = Paths.get("src", "test", "resources", "testTrustStore.jks");
    private static final String TRUST_STORE_TYPE = "JKS";

    @Test
    public void testCreateSslSocketFactory_canCreateWithAllParams() {
        SSLSocketFactory factory = TrustStores.createSslSocketFactory(
                TrustStoreConfiguration.builder()
                .trustStorePath(TRUST_STORE_PATH)
                .trustStoreType(TRUST_STORE_TYPE)
                .trustStorePassword(TRUST_STORE_PASSWORD)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithoutPassword() {
        SSLSocketFactory factory = TrustStores.createSslSocketFactory(
                TrustStoreConfiguration.builder()
                .trustStorePath(TRUST_STORE_PATH)
                .trustStoreType(TRUST_STORE_TYPE)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithoutType() {
        SSLSocketFactory factory = TrustStores.createSslSocketFactory(
                TrustStoreConfiguration.builder()
                .trustStorePath(TRUST_STORE_PATH)
                .trustStorePassword(TRUST_STORE_PASSWORD)
                .build());

        assertThat(factory, notNullValue());
    }

    @Test
    public void testCreateSslSocketFactory_canCreateWithOnlyPath() {
        SSLSocketFactory factory = TrustStores.createSslSocketFactory(
                TrustStoreConfiguration.builder()
                .trustStorePath(TRUST_STORE_PATH)
                .build());

        assertThat(factory, notNullValue());
    }

}
