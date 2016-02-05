/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.trust;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;

public final class TrustStoreTests {

    static final String TRUST_STORE_PASSWORD = "testCA";
    static final Path TRUST_STORE_PATH = Paths.get("src", "test", "resources", "testCA", "testCATrustStore.jks");
    static final String TRUST_STORE_TYPE = "JKS";

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
