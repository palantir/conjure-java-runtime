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

package com.palantir.remoting.clients;

import static org.mockito.Mockito.mock;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class ClientConfigTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void noPartialSslConfig_whenSocketFactoryIsGiven() throws Exception {
        SSLSocketFactory sslSocketFactory = mock(SSLSocketFactory.class);
        ClientConfig.Builder config = ClientConfig.builder().sslSocketFactory(sslSocketFactory);
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Must set either both sslSocketFactory and TrustManager, or neither");
        config.build();
    }

    @Test
    public void noPartialSslConfig_whenTrustManagerIsGiven() throws Exception {
        X509TrustManager trustManager = mock(X509TrustManager.class);
        ClientConfig.Builder config = ClientConfig.builder().trustManager(trustManager);
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("Must set either both sslSocketFactory and TrustManager, or neither");
        config.build();
    }
}
