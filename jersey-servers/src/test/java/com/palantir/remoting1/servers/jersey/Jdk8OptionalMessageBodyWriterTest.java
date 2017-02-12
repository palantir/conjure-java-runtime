/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting1.servers.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.jersey.DropwizardResourceConfig;
import java.util.Optional;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

public final class Jdk8OptionalMessageBodyWriterTest extends JerseyTest {

    @Override
    protected Application configure() {
        forceSet(TestProperties.CONTAINER_PORT, "0");
        return DropwizardResourceConfig.forTesting(new MetricRegistry())
                .register(HttpRemotingJerseyFeature.DEFAULT)
                .register(OptionalReturnResource.class);
    }

    @Test
    public void presentOptionalsReturnTheirValue() throws Exception {
        assertThat(target("/optional-return/")
                .queryParam("id", "woo").request()
                .get(String.class))
                .isEqualTo("woo");
    }

    @Test
    public void absentOptionalsThrowANotFound() throws Exception {
        Response response = target("/optional-return/").request().get();
        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Path("/optional-return/")
    @Produces(MediaType.TEXT_PLAIN)
    public static final class OptionalReturnResource {
        @GET
        public Optional<String> showWithQueryParam(@QueryParam("id") String id) {
            return Optional.ofNullable(id);
        }

        @POST
        public Optional<String> showWithFormParam(@FormParam("id") String id) {
            return Optional.ofNullable(id);
        }
    }
}
