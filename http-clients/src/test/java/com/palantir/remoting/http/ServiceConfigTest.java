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

package com.palantir.remoting.http;

import static org.junit.Assert.assertEquals;

import com.google.common.base.Optional;
import com.palantir.config.service.ServiceConfiguration;
import com.palantir.config.service.ServiceDiscoveryConfiguration;
import com.palantir.remoting.http.ServiceConfigTestServer.AuthService;
import com.palantir.remoting.http.ServiceConfigTestServer.GoodbyeService;
import com.palantir.remoting.http.ServiceConfigTestServer.HelloService;
import com.palantir.remoting.ssl.SslSocketFactories;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.util.HashSet;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Rule;
import org.junit.Test;

public final class ServiceConfigTest {
    @Rule
    public final DropwizardAppRule<ServiceConfigTestAppConfig> rule =
            new DropwizardAppRule<ServiceConfigTestAppConfig>(ServiceConfigTestServer.ServiceConfigTestApp.class,
                    ServiceConfigTest.class.getClassLoader().getResource("service-config-example.yml").getPath());

    @Test
    public void testResource() {
        ServiceDiscoveryConfiguration discoveryConfiguration =
                rule.getConfiguration().getServiceDiscoveryConfiguration();

        HelloService helloClient =
                FeignClients.standard("test suite user agent").createProxy(
                        discoveryConfiguration, "hello", HelloService.class);
        GoodbyeService goodbyeClient =
                FeignClients.standard("test suite user agent").createProxy(
                        discoveryConfiguration, "goodbye", GoodbyeService.class);

        ServiceConfiguration authConfig = rule.getConfiguration().getAuthConfiguration();
        Optional<SSLSocketFactory> socketFactory =
                Optional.of(SslSocketFactories.createSslSocketFactory(authConfig.security().get()));
        AuthService authClient =
                FeignClients.standard("test suite user agent").createProxy(socketFactory,
                        new HashSet<>(authConfig.uris()), AuthService.class);

        assertEquals("Hello world!", helloClient.sayHello());
        assertEquals("Goodbye world!", goodbyeClient.sayGoodBye());
        assertEquals("You are safe!", authClient.isSecured());
    }
}
