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

import static com.google.common.base.Preconditions.checkState;

import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractConfiguredBundle<C extends Configuration> implements ConfiguredBundle<C> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final AtomicReference<String> appNameRef = new AtomicReference<>();

    @Override
    public final void initialize(Bootstrap<?> bootstrap) {
        String appName = bootstrap.getApplication().getClass().getSimpleName();
        appNameRef.set(appName);
        log().info("Initializing bundle {} for application {}", getClass().getSimpleName(), appName());
        bootstrap(bootstrap);
    }

    /**
     * Callback for initializing this module as part of application bootstrap.
     *
     * @param bootstrap the application bootstrap
     */
    protected void bootstrap(Bootstrap<?> bootstrap) {
    }

    @Override
    public final void run(C configuration, Environment environment) throws Exception {
        log().info("Running bundle {} for application {}", getClass().getSimpleName(), appName());
        start(configuration, environment);
    }

    /**
     * Starts the module with specified configuration and environment.
     *
     * @param configuration the configuration object
     * @param environment the application's {@link Environment}
     * @throws Exception if an error occurs during start up.
     */
    protected void start(C configuration, Environment environment) throws Exception {
    }

    protected final String appName() {
        String name = appNameRef.get();
        checkState(name != null, "Application name has not been initialized yet");
        return name;
    }

    protected final Logger log() {
        return log;
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "{application='" + appNameRef + "'}";
    }

}
