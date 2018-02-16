/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.remoting3.servers.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.MetricRegistry;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting.api.errors.ErrorType;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting.api.errors.ServiceException;
import io.dropwizard.jersey.DropwizardResourceConfig;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

public final class JerseyExceptionMapperTest extends JerseyTest {

    @Override
    protected Application configure() {
        forceSet(TestProperties.CONTAINER_PORT, "0");
        return DropwizardResourceConfig.forTesting(new MetricRegistry())
                .register(HttpRemotingJerseyFeature.INSTANCE)
                .register(AngryResource.class);
    }

    @Test
    public void testServiceException() {
        Response response = target("/angry/service").request().get();
        assertThat(response.getStatus()).isEqualTo(400);
        SerializableError entity = response.readEntity(SerializableError.class);
        assertThat(entity.parameters().get("message")).isEqualTo("Hello");
    }

    @Test
    public void testWebException() {
        Response response = target("/angry/web").request().get();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.readEntity(String.class)).doesNotContain("secret");
    }

    @Test
    public void testRuntimeException() {
        Response response = target("/angry/runtime").request().get();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.readEntity(String.class)).contains(RuntimeException.class.getName());
    }

    @Path("angry")
    @Produces(MediaType.TEXT_PLAIN)
    public static final class AngryResource {
        @GET
        @Path("service")
        public void serviceException() {
            throw new ServiceException(ErrorType.INVALID_ARGUMENT, SafeArg.of("message", "Hello"));
        }

        @GET
        @Path("web")
        public void webApplicationException() {
            throw new ForbiddenException("secret");
        }

        @GET
        @Path("runtime")
        public void runtimeException() {
            throw new RuntimeException("Run Forrest, run!");
        }
    }
}
