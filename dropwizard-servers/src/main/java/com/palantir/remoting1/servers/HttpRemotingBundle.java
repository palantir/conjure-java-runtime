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

import static com.google.common.base.Preconditions.checkNotNull;

import com.github.kristofa.brave.Brave;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dropwizard bundle for initializing HTTP remoting client and server services.
 * Users should
 */
// TODO (davids) //        & TracingConfigurationProvider>
public final class HttpRemotingBundle<C extends Configuration>
        extends AbstractConfiguredBundle<C> {

    private static final Logger log = LoggerFactory.getLogger(HttpRemotingBundle.class);

    private final BraveBundle<C> braveBundle;

    public HttpRemotingBundle() {
        this(new BraveBundle<C>());
    }

    public HttpRemotingBundle(BraveBundle<C> braveBundle) {
        this.braveBundle = checkNotNull(braveBundle, "Brave bundle cannot be null");
    }

    @Override
    protected void bootstrap(Bootstrap bootstrap) {
        braveBundle.initialize(bootstrap);
        log.info("Setting up tracing for {}", appName());
    }

    @Override
    protected void start(C configuration, Environment environment) throws Exception {
        braveBundle.run(configuration, environment);
        DropwizardServers.configureStacktraceMappers(environment, DropwizardServers.Stacktraces.PROPAGATE);
    }

    public BraveBundle braveBundle() {
        return braveBundle;
    }

    public Brave brave() {
        return braveBundle.brave();
    }

}
