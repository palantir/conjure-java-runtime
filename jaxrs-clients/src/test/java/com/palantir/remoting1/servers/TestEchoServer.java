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
import io.dropwizard.setup.Environment;

public final class TestEchoServer extends Application<Configuration> {

    public static void main(String[] args) throws Exception {
        new TestEchoServer().run(args);
    }

    @Override
    public void run(Configuration config, final Environment env) throws Exception {
        env.jersey().register(new TestEchoService() {
            @Override
            public String echo(String value) {
                logBraveState();
                //noinspection unused - try-with-resources
                try (Tracer.TraceContext trace = Tracers.activeTracer()
                        .begin(TestEchoServer.class.getSimpleName(), "echo")) {
                    logBraveState();
                    return value;
                }
            }
        });
        DropwizardServers.configure(env, config, getClass().getName(),
                DropwizardServers.Stacktraces.DO_NOT_PROPAGATE);
    }

    private void logBraveState() {
        TestSupport.logDebugBrave(getClass().getSimpleName(), getLogger());
    }

    private ch.qos.logback.classic.Logger getLogger() {
        return TestSupport.getLogger(getClass());
    }

}
