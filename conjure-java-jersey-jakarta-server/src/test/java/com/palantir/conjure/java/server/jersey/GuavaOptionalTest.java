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

import com.google.common.base.Strings;
import com.palantir.undertest.UndertowServerExtension;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class GuavaOptionalTest {

    @RegisterExtension
    public static final UndertowServerExtension undertow = UndertowServerExtension.create()
            .jersey(ConjureJerseyFeature.INSTANCE)
            .jersey(new OptionalTestResource());

    @Test
    public void testOptionalPresent() throws SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/optional")
                        .addParameter("value", "val")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("valval");
                });
    }

    @Test
    public void testOptionalAbsent() {
        undertow.get("/optional", response -> {
            assertThat(response.getCode()).isEqualTo(Status.NO_CONTENT.getStatusCode());
        });
    }

    public static final class OptionalTestResource implements OptionalTestService {
        @Override
        public com.google.common.base.Optional<String> getOptional(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                return com.google.common.base.Optional.absent();
            } else {
                return com.google.common.base.Optional.of(value + value);
            }
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface OptionalTestService {
        @GET
        @Path("/optional")
        com.google.common.base.Optional<String> getOptional(@QueryParam("value") @Nullable String value);
    }
}
