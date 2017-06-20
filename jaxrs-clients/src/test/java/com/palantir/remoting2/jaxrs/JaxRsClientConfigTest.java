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

package com.palantir.remoting2.jaxrs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.palantir.remoting2.clients.ClientConfiguration;
import com.palantir.remoting2.clients.ImmutableClientConfiguration;
import com.palantir.remoting2.servers.jersey.HttpRemotingJerseyFeature;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.ClassRule;
import org.junit.Test;

public final class JaxRsClientConfigTest extends TestBase {
    @ClassRule
    public static final DropwizardAppRule<Configuration> ECHO_SERVER =
            new DropwizardAppRule<>(TestEchoServer.class, "src/test/resources/test-server-ssl.yml");

    @Test
    public void testSslSocketFactory_cannotConnectWhenSocketFactoryIsNotSet() throws Exception {
        String endpointUri = "https://localhost:" + ECHO_SERVER.getLocalPort();
        TestService service = JaxRsClient.create(TestService.class, "agent", createTestConfig(endpointUri));

        try {
            service.echo("foo");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(),
                    containsString("sun.security.validator.ValidatorException: PKIX path building failed:"));
        }
    }

    @Test
    public void testSslSocketFactory_canConnectWhenSocketFactoryIsSet() throws Exception {
        String endpointUri = "https://localhost:" + ECHO_SERVER.getLocalPort();
        TestService service = JaxRsClient.create(TestService.class, "agent", createTestConfig(endpointUri));
        assertThat(service.echo("foo"), is("foo"));
    }

    @Test
    public void testRetries_notSupported() throws Exception {
        try {
            ClientConfiguration config = ImmutableClientConfiguration.builder()
                    .from(createTestConfig("uri"))
                    .maxNumRetries(1)
                    .build();
            JaxRsClient.create(TestService.class, "agent", config);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Connection-level retries are not supported by JaxRsClient"));
        }
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
