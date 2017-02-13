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

package com.palantir.remoting2.jaxrs.feignimpl;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

public final class ServiceConfigTestServer {

    public static final class ServiceConfigTestApp extends Application<ServiceConfigTestAppConfig> {

        @Override
        public void initialize(Bootstrap<ServiceConfigTestAppConfig> bootstrap) {
            bootstrap.getObjectMapper().registerModule(new Jdk8Module());
        }

        @Override
        public void run(ServiceConfigTestAppConfig configuration, Environment environment) throws Exception {
            environment.jersey().register(new GreetingResource());
        }

        public static final class GreetingResource implements HelloService, GoodbyeService {

            @Override
            public String sayHello() {
                return "Hello world!";
            }

            @Override
            public String sayGoodBye() {
                return "Goodbye world!";
            }
        }
    }

    @Path("/")
    public interface GoodbyeService {

        @GET
        @Path("goodbye")
        @Produces(MediaType.TEXT_PLAIN)
        String sayGoodBye();
    }

    @Path("/")
    public interface HelloService {

        @GET
        @Path("hello")
        @Produces(MediaType.TEXT_PLAIN)
        String sayHello();
    }
}
