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

package com.palantir.remoting3.jaxrs.feignimpl;

import static org.junit.Assert.assertEquals;

import com.palantir.remoting.api.config.service.ServiceConfigurationFactory;
import com.palantir.remoting3.clients.ClientConfigurations;
import com.palantir.remoting3.jaxrs.JaxRsClient;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Rule;
import org.junit.Test;

public final class ServiceConfigTest {

    @Rule
    public final DropwizardAppRule<ServiceConfigTestAppConfig> rule =
            new DropwizardAppRule<>(ServiceConfigTestServer.ServiceConfigTestApp.class,
                    ServiceConfigTest.class.getClassLoader().getResource("service-config-example.yml").getPath());

    @Test
    public void testResource() {
        ServiceConfigurationFactory factory =
                ServiceConfigurationFactory.of(rule.getConfiguration().getServiceDiscoveryConfiguration());

        ServiceConfigTestServer.HelloService helloClient = JaxRsClient.create(
                ServiceConfigTestServer.HelloService.class, "agent", ClientConfigurations.of(factory.get("hello")));
        ServiceConfigTestServer.GoodbyeService goodbyeClient = JaxRsClient.create(
                ServiceConfigTestServer.GoodbyeService.class,
                "agent",
                ClientConfigurations.of(factory.get("goodbye")));

        assertEquals("Hello world!", helloClient.sayHello());
        assertEquals("Goodbye world!", goodbyeClient.sayGoodBye());
    }
}
