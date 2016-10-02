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

package com.palantir.remoting1.servers;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Rule;
import org.junit.Test;

public final class AbstractConfiguredBundleTest {

    @Rule
    public DropwizardAppRule<Configuration> appRule = new DropwizardAppRule<>(
            TestServer.class, "src/test/resources/test-server.yml");

    @Test
    public void testCallbacksInvoked() throws Exception {
        TestServer server = appRule.getApplication();
        assertThat(server.getTestBundle().isBootstrapped(), equalTo(true));
        assertThat(server.getTestBundle().isStarted(), equalTo(true));
    }

    @Test
    public void testAppName() throws Exception {
        TestServer server = appRule.getApplication();
        assertThat(server.getTestBundle().appName(), equalTo("TestServer"));
    }

    public static final class TestServer extends Application<Configuration> {
        private final TestConfiguredBundle testBundle = new TestConfiguredBundle();

        @Override
        public void initialize(Bootstrap<Configuration> bootstrap) {
            bootstrap.addBundle(getTestBundle());
        }

        @Override
        public void run(Configuration configuration, Environment environment) throws Exception {
        }

        TestConfiguredBundle getTestBundle() {
            return testBundle;
        }

        static final class TestConfiguredBundle extends AbstractConfiguredBundle<Configuration> {
            private boolean isBootstrapped = false;
            private boolean isStarted = false;

            @Override
            protected void bootstrap(Bootstrap<?> bootstrap) {
                super.bootstrap(bootstrap);
                isBootstrapped = true;
                log().info("Bootstrapped: {}", isBootstrapped());
            }

            @Override
            protected void start(Configuration configuration, Environment environment) throws Exception {
                super.start(configuration, environment);
                isStarted = true;
                log().info("Started: {}", isStarted());
            }

            boolean isBootstrapped() {
                return isBootstrapped;
            }

            boolean isStarted() {
                return isStarted;
            }

        }
    }

}
