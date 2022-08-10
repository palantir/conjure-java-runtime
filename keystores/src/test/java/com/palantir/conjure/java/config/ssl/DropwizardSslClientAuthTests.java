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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import feign.Feign;
import feign.RetryableException;
import feign.jaxrs.JAXRSContract;
import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(DropwizardExtensionsSupport.class)
public final class DropwizardSslClientAuthTests {

    public static final DropwizardAppExtension<Configuration> APP = new DropwizardAppExtension<>(
            TestEchoServer.class,
            "src/test/resources/test-server.yml",
            ConfigOverride.config(
                    "server.applicationConnectors[0].keyStorePath", TestConstants.SERVER_KEY_STORE_JKS_PATH.toString()),
            ConfigOverride.config(
                    "server.applicationConnectors[0].keyStorePassword", TestConstants.SERVER_KEY_STORE_JKS_PASSWORD),
            ConfigOverride.config(
                    "server.applicationConnectors[0].trustStorePath", TestConstants.CA_TRUST_STORE_PATH.toString()),
            ConfigOverride.config(
                    "server.applicationConnectors[0].trustStorePassword", TestConstants.CA_TRUST_STORE_JKS_PASSWORD),
            ConfigOverride.config(
                    "server.applicationConnectors[0].crlPath", TestConstants.COMBINED_CRL_PATH.toString()),
            ConfigOverride.config("server.applicationConnectors[0].needClientAuth", "true"));

    @Test
    public void testConnectionFailsWithoutClientCerts() {
        SslConfiguration sslConfig = SslConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        TestEchoService service = createTestService(sslConfig);

        assertThatThrownBy(() -> service.echo("foo")).isInstanceOf(RetryableException.class);
    }

    @Test
    public void testConnectionWorksWithClientCerts() {
        SslConfiguration sslConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.CLIENT_KEY_STORE_JKS_PATH,
                TestConstants.CLIENT_KEY_STORE_JKS_PASSWORD);
        TestEchoService service = createTestService(sslConfig);

        assertThat(service.echo("foo")).isEqualTo("foo");
    }

    @Test
    public void testConnectionWorksWithClientCertsWithIntermediateCa() {
        SslConfiguration sslConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.CHILD_KEY_CERT_CHAIN_PEM_PATH)
                .keyStorePassword("")
                .keyStoreType(SslConfiguration.StoreType.PEM)
                .build();
        TestEchoService service = createTestService(sslConfig);

        assertThat(service.echo("foo")).isEqualTo("foo");
    }

    private static TestEchoService createTestService(SslConfiguration sslConfig) {
        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(sslConfig);
        X509TrustManager trustManager = SslSocketFactories.createX509TrustManager(sslConfig);

        String endpointUri = "https://localhost:" + APP.getLocalPort();
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .sslSocketFactory(factory, trustManager)
                .build();
        return Feign.builder()
                .client(new feign.okhttp.OkHttpClient(okHttpClient))
                .contract(new JAXRSContract())
                .target(TestEchoService.class, endpointUri);
    }

    public static final class TestEchoServer extends Application<Configuration> {
        @Override
        public void run(Configuration _config, final Environment env) throws Exception {
            env.jersey().register(new TestEchoResource());
        }

        public static final class TestEchoResource implements TestEchoService {
            @Override
            public String echo(String value) {
                return value;
            }
        }
    }

    @Path("/")
    public interface TestEchoService {
        @GET
        @Path("/echo")
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        String echo(@QueryParam("value") String value);
    }
}
