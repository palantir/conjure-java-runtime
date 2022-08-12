/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.server.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.undertest.UndertowServerExtension;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class DeprecationTest {

    @RegisterExtension
    public static final UndertowServerExtension undertow = UndertowServerExtension.create()
            .jersey(ConjureJerseyFeature.INSTANCE)
            .jersey(new DeprecationResource());

    ;

    @Test
    public void testDeprecated() throws SecurityException {
        undertow.runRequest(new HttpGet("/deprecated"), response -> {
            assertThat(response.getCode()).isEqualTo(Status.NO_CONTENT.getStatusCode());
            assertThat(response.getFirstHeader("deprecation").getValue()).isEqualTo("true");
        });
    }

    @Test
    public void testUnmarked() throws SecurityException {
        undertow.runRequest(new HttpGet("/unmarked"), response -> {
            assertThat(response.getCode()).isEqualTo(Status.NO_CONTENT.getStatusCode());
            assertThat(response.getFirstHeader("deprecation")).isNull();
        });
    }

    public static final class DeprecationResource implements DeprecationTestService {
        @Override
        public void deprecated() {}

        @Override
        public void unmarked() {}
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface DeprecationTestService {
        /**
         * Deprecated endpoint.
         *
         * @deprecated for testing
         */
        @GET
        @Deprecated
        @Path("/deprecated")
        void deprecated();

        @GET
        @Path("/unmarked")
        void unmarked();
    }
}
