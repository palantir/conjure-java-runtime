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

package com.palantir.remoting.http.server;

import org.glassfish.jersey.server.ResourceConfig;

public final class JerseyComponents {

    private JerseyComponents() {}

    /**
     * Registers standard exception mappers and message body writers.
     *
     * @param includeStackTrace
     *        include the stack trace in exceptions written to the BODY of the http response; turn off when clients are
     *        untrusted and shouldn't know details about the server implementation
     */
    // DEV NOTE: using jersey2's ResourceConfig since jersey2 is required anyway for using NoContentException
    public static void registerComponents(ResourceConfig config, boolean includeStackTrace) {
        // exception mappers
        config.register(new IllegalArgumentExceptionMapper(includeStackTrace));
        config.register(new NoContentExceptionMapper());
        config.register(new RuntimeExceptionMapper(includeStackTrace));
        config.register(new WebApplicationExceptionMapper(includeStackTrace));

        // message body writers
        config.register(new OptionalAsNoContentMessageBodyWriter());
    }

}
