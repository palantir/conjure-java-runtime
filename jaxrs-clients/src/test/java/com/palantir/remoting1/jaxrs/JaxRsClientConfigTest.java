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

package com.palantir.remoting1.jaxrs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.palantir.remoting1.clients.ClientConfig;
import com.palantir.remoting1.config.ssl.SslConfiguration;
import com.palantir.remoting1.config.ssl.SslSocketFactories;
import com.palantir.remoting1.servers.jersey.HttpRemotingJerseyFeature;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.nio.file.Paths;
import org.junit.ClassRule;
import org.junit.Test;

public final class JaxRsClientConfigTest {
    @ClassRule
    public static final DropwizardAppRule<Configuration> ECHO_SERVER =
            new DropwizardAppRule<>(TestEchoServer.class, "src/test/resources/test-server-ssl.yml");

    @Test
    public void testSslSocketFactory_cannotConnectWhenSocketFactoryIsNotSet() throws Exception {
        String endpointUri = "https://localhost:" + ECHO_SERVER.getLocalPort();
        TestService service = JaxRsClient.builder().build(TestService.class, "agent", endpointUri);

        try {
            service.echo("foo");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(),
                    containsString("sun.security.validator.ValidatorException: PKIX path building failed:"));
        }
    }

    @Test
    public void testSslSocketFactory_canConnectWhenSocketFactoryIsSet() throws Exception {
        TestService service = createProxy(ECHO_SERVER.getLocalPort(), "test");
        assertThat(service.echo("foo"), is("foo"));
    }

    @Test
    public void testRetries_notSupported() throws Exception {
        try {
            JaxRsClient.builder(ClientConfig.builder().maxNumRetries(1).build())
                    .build(TestService.class, "agent", "uri");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Connection-level retries are not supported by JaxRsClient"));
        }
    }

    private static TestService createProxy(int port, String name) {
        String endpointUri = "https://localhost:" + port;
        SslConfiguration sslConfig = SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks"));
        return JaxRsClient.builder(
                ClientConfig.builder().trustContext(SslSocketFactories.createTrustContext(sslConfig)).build())
                .build(TestService.class, name, endpointUri);
    }

    public static final class TestEchoServer extends Application<Configuration> {
        @Override
        public void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(HttpRemotingJerseyFeature.DEFAULT);
            env.jersey().register(new TestEchoResource());
        }

        private static final class TestEchoResource implements TestService {
            @Override
            public String echo(String value) {
                return value;
            }
        }
    }
}
