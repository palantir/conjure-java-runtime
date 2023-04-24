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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;

class SslUtilsTest {

    @Test
    public void testExtractX509TrustManager_extractsFirstManagerIf509() {
        SslConfiguration sslConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .trustStoreType(TestConstants.CA_TRUST_STORE_TYPE)
                .build();

        X509TrustManager x509TrustManager = mock(X509TrustManager.class);

        TrustManager[] trustManagers = new TrustManager[] {x509TrustManager};

        X509TrustManager extractedManager = SslUtils.extractX509TrustManager(trustManagers, sslConfig);

        assertThat(extractedManager).isEqualTo(x509TrustManager);
    }

    @Test
    public void testExtractX509TrustManager_throwsIfFirstManagerNotX509() {
        SslConfiguration sslConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .trustStoreType(TestConstants.CA_TRUST_STORE_TYPE)
                .build();

        TrustManager nonX509TrustManager = new DummyTrustManager();
        TrustManager[] trustManagers = new TrustManager[] {nonX509TrustManager};

        assertThatThrownBy(() -> SslUtils.extractX509TrustManager(trustManagers, sslConfig))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("First TrustManager associated with SslConfiguration was expected to be a X509TrustManager,"
                        + " but was a DummyTrustManager: src/test/resources/testCA/testCA.jks");
    }

    private static final class DummyTrustManager implements TrustManager {}
}
