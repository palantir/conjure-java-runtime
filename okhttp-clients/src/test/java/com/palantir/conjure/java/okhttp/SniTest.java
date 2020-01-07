/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import java.nio.file.Paths;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIServerName;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;

public final class SniTest extends TestBase {

    private static final String URL = "https://localhost:9000";

    private OkHttpClient client;

    @Before
    public void before() {
        client = OkHttpClients.create(createTestConfig(URL), AGENT, NoOpHostEventsSink.INSTANCE, SniTest.class);
    }

    @Test
    public void name() throws IOException {
        SslConfiguration sslConfig = SslConfiguration.of(
                Paths.get("src/test/resources/trustStore.jks"),
                Paths.get("src/test/resources/keyStore.jks"),
                "keystore");

        Undertow server = Undertow.builder()
                .addHttpsListener(9000, "localhost", SslSocketFactories.createSslContext(sslConfig))
                .setHandler(exchange -> exchange
                        .setStatusCode(200)
                        .getResponseSender().send(getSniServerName(exchange)))
                .build();

        server.start();

        String result = client.newCall(new Request.Builder().get().url(URL + "/test").build())
                .execute()
                .body()
                .string();
        assertThat(result).isEqualTo("localhost");

        server.stop();
    }

    private String getSniServerName(HttpServerExchange exchange) {
        ExtendedSSLSession sslSession =
                (ExtendedSSLSession) exchange.getConnection().getSslSessionInfo().getSSLSession();
        SNIServerName sni = Iterables.getOnlyElement(sslSession.getRequestedServerNames());
        return new String(sni.getEncoded());
    }
}
