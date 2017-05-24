/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http.server;

import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;

public final class DropwizardComponents {

    private DropwizardComponents() {}

    /**
     * Registers standard exception mappers and message body writers.
     *
     * @see DropwizardTracingFilters
     * @see JerseyComponents
     */
    public static void registerComponents(Environment environment, Configuration config, String tracerName,
            boolean includeStackTrace) {
        DropwizardTracingFilters.registerTracers(environment, config, tracerName);
        JerseyComponents.registerComponents(environment.jersey().getResourceConfig(), includeStackTrace);
    }

}
