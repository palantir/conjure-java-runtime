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

import com.palantir.remoting1.jaxrs.TestEchoService;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public final class ProxyingEchoServer extends Application<Configuration> {

    private volatile int echoServerPort;
    private final HttpRemotingBundle<Configuration> httpRemotingBundle = new HttpRemotingBundle<>();

    @SuppressWarnings("unused") // instantiated by DropwizardAppRule
    public ProxyingEchoServer() {
        this(0);
    }

    public ProxyingEchoServer(int echoServerPort) {
        this.echoServerPort = echoServerPort;
    }

    public void setEchoServerPort(int echoServerPort) {
        this.echoServerPort = echoServerPort;
    }

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {
        super.initialize(bootstrap);
        bootstrap.addBundle(httpRemotingBundle);
    }

    @Override
    public void run(Configuration config, final Environment env) throws Exception {
        env.jersey().register(new TestEchoService() {
            @Override
            public String echo(String value) {
                //noinspection unused - try-with-resources
                logBraveState();

                TestEchoService echoService = TestSupport.createProxy(echoServerPort, "proxyingClient",
                        httpRemotingBundle.brave());
                String echo = echoService.echo(value);

                getLogger().info("Finished proxying echo server with '{}'", value);
                logBraveState();
                return echo;
            }
        });
    }

    private void logBraveState() {
        TestSupport.logDebugBrave(getClass().getSimpleName(),
                getLogger(),
                httpRemotingBundle.brave());
    }

    private ch.qos.logback.classic.Logger getLogger() {
        return TestSupport.getLogger(getClass());
    }

}
