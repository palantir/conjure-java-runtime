/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.ssl;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import com.palantir.remoting.http.FeignClients;
import com.palantir.remoting.ssl.KeyStoreConfiguration;
import com.palantir.remoting.ssl.SslConfiguration;
import com.palantir.remoting.ssl.SslSocketFactories;
import com.palantir.remoting.ssl.TrustStoreConfiguration;
import feign.RetryableException;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.junit.ClassRule;
import org.junit.Test;

public final class DropwizardSslClientAuthTests {
    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(
            TestEchoServer.class,
            "src/test/resources/test-server.yml",
            ConfigOverride.config("server.applicationConnectors[0].keyStorePath",
                    TestConstants.SERVER_KEY_STORE_JKS_PATH.toString()),
            ConfigOverride.config("server.applicationConnectors[0].keyStorePassword",
                    TestConstants.SERVER_KEY_STORE_JKS_PASSWORD),
            ConfigOverride.config("server.applicationConnectors[0].trustStorePath",
                    TestConstants.CA_TRUST_STORE_PATH.toString()),
            ConfigOverride.config("server.applicationConnectors[0].crlPath",
                    TestConstants.CA_CRL_PATH.toString()),
            ConfigOverride.config("server.applicationConnectors[0].needClientAuth",
                    "true"));

    @Test
    public void testConnectionFailsWithoutClientCerts() {
        SslConfiguration sslConfig = SslConfiguration.of(TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH));
        TestEchoService service = createTestService(sslConfig);

        try {
            service.echo("foo");
            fail();
        } catch (RetryableException ex) {
            // expected
        }
    }

    @Test
    public void testConnectionWorksWithClientCerts() {
        SslConfiguration sslConfig = SslConfiguration.of(
                TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH),
                KeyStoreConfiguration.of(
                        TestConstants.CLIENT_KEY_STORE_JKS_PATH,
                        TestConstants.CLIENT_KEY_STORE_JKS_PASSWORD));
        TestEchoService service = createTestService(sslConfig);

        assertThat(service.echo("foo"), is("foo"));
    }

    private static TestEchoService createTestService(SslConfiguration sslConfig) {
        SSLSocketFactory factory = SslSocketFactories.createSslSocketFactory(sslConfig);

        String endpointUri = "https://localhost:" + APP.getLocalPort();
        return FeignClients.standard().createProxy(
                Optional.of(factory),
                endpointUri,
                TestEchoService.class);
    }

    public static final class TestEchoServer extends Application<Configuration> {
        @Override
        public void run(Configuration config, final Environment env) throws Exception {
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
