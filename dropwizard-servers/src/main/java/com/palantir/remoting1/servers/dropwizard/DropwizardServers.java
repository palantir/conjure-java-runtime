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

package com.palantir.remoting1.servers.dropwizard;

import com.palantir.remoting1.servers.jersey.ExceptionMappers;
import com.palantir.remoting1.servers.jersey.TraceEnrichingFilter;
import io.dropwizard.setup.Environment;
import javax.ws.rs.ext.ExceptionMapper;


public final class DropwizardServers {
    private DropwizardServers() {}

    /**
     * Server-side stacktraces are serialized and transferred to the client iff {@code serializeStacktrace} is {@code
     * true}. Configures a Dropwizard/Jersey server w.r.t. http-remoting conventions: registers tracer filters and
     * exception mappers.
     */
    public static void configure(
            final Environment environment, ExceptionMappers.StacktracePropagation stacktracePropagation) {
        ExceptionMappers.visitExceptionMappers(
                stacktracePropagation,
                new ExceptionMappers.Consumer<ExceptionMapper<? extends Throwable>>() {
                    @Override
                    public void accept(ExceptionMapper<? extends Throwable> mapper) {
                        environment.jersey().register(mapper);
                    }
                });
        environment.jersey().register(new OptionalAsNoContentMessageBodyWriter());
        environment.jersey().register(new TraceEnrichingFilter());
    }
}
