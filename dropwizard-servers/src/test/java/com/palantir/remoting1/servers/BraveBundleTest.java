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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import com.github.kristofa.brave.Brave;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Rule;
import org.junit.Test;

public final class BraveBundleTest {

    @Rule
    public DropwizardAppRule<Configuration> appRule = new DropwizardAppRule<>(
            TestServer.class, "src/test/resources/test-server.yml");

    @Test
    public void testBraveInitialized() throws Exception {
        TestServer server = appRule.getApplication();
        Brave brave = server.braveBundle().brave();
        assertThat(brave, is(notNullValue()));
        assertThat(server.isRunning(), equalTo(true));

        brave.serverTracer().setServerReceived();
        brave.localTracer().startNewSpan("testComponent", "operation");
        brave.localTracer().finishSpan(123L);
        brave.serverTracer().setServerSend();
    }

    @Test
    public void testAppName() throws Exception {
        TestServer server = appRule.getApplication();
        assertThat(server.braveBundle().appName(), equalTo("TestServer"));
    }

    public static final class TestServer extends Application<Configuration> {
        private final BraveBundle<Configuration> braveBundle = new BraveBundle<>();
        private boolean isRunning;

        @Override
        public void initialize(Bootstrap<Configuration> bootstrap) {
            bootstrap.addBundle(braveBundle());
        }

        @Override
        public void run(Configuration configuration, Environment environment) throws Exception {
            isRunning = true;
        }

        boolean isRunning() {
            return isRunning;
        }

        public BraveBundle<Configuration> braveBundle() {
            return braveBundle;
        }
    }
}
