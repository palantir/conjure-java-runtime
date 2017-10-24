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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.palantir.remoting.api.config.service.ServiceConfigurationFactory;
import com.palantir.remoting.api.config.service.ServicesConfigBlock;
import com.palantir.remoting3.clients.ClientConfigurations;
import com.palantir.remoting3.jaxrs.JaxRsClient;
import com.palantir.remoting3.jaxrs.TestService;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
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

        TestService full = JaxRsClient.create(
                TestService.class, "agent", ClientConfigurations.of(factory.get("full")));
        TestService minimal = JaxRsClient.create(
                TestService.class, "agent", ClientConfigurations.of(factory.get("minimal")));

        assertEquals("string", full.string());
        assertEquals("string", minimal.string());
    }

    /**
     * Configuration class for the {@link ServiceConfigTestServer}.
     */
    static final class ServiceConfigTestAppConfig extends Configuration {

        @JsonProperty("serviceDiscovery")
        private ServicesConfigBlock serviceDiscoveryConfig;

        public ServicesConfigBlock getServiceDiscoveryConfiguration() {
            return this.serviceDiscoveryConfig;
        }
    }

    static final class ServiceConfigTestServer {

        public static final class ServiceConfigTestApp extends Application<ServiceConfigTestAppConfig> {

            @Override
            public void initialize(Bootstrap<ServiceConfigTestAppConfig> bootstrap) {
                bootstrap.getObjectMapper().registerModule(new Jdk8Module());
            }

            @Override
            public void run(ServiceConfigTestAppConfig configuration, Environment environment) throws Exception {
                environment.jersey().register(new Resource());
            }

            public static final class Resource implements TestService {

                @Override
                public String string() {
                    return "string";
                }

                @Override
                public String param(String param) {
                    return param;
                }
            }
        }
    }
}
