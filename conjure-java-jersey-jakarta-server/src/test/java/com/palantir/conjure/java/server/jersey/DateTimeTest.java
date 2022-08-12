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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class DateTimeTest {

    @RegisterExtension
    public static final UndertowServerExtension undertow = UndertowServerExtension.create()
            .jersey(ConjureJerseyFeature.INSTANCE)
            .jersey(new DateTimeTestResource());

    @Test
    public void testOffsetDateTimeParam() throws SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/offsetDateTime")
                        .addParameter("value", "2017-01-02T03:04:05.06Z")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("2017-01-02T03:04:05.060Z");
                });
    }

    @Test
    public void testZonedDateTimeParam() throws SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/zonedDateTime")
                        .addParameter("value", "2017-01-02T03:04:05.06Z")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("2017-01-02T03:04:05.060Z");
                });
    }

    @Test
    public void testInstantParam() {
        undertow.runRequest(
                ClassicRequestBuilder.get("/instant")
                        .addParameter("value", "2017-01-02T03:04:05.06Z")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("2017-01-02T03:04:05.060Z");
                });
    }

    public static final class DateTimeTestResource implements DateTimeTestService {
        @Override
        public String getOffsetDateTime(OffsetDateTime value) {
            return value.toString();
        }

        @Override
        public String getZonedDateTime(ZonedDateTime value) {
            return value.toString();
        }

        @Override
        public String getInstant(Instant value) {
            return value.toString();
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface DateTimeTestService {
        @GET
        @Path("/offsetDateTime")
        String getOffsetDateTime(@QueryParam("value") OffsetDateTime value);

        @GET
        @Path("/zonedDateTime")
        String getZonedDateTime(@QueryParam("value") ZonedDateTime value);

        @GET
        @Path("/instant")
        String getInstant(@QueryParam("value") Instant value);
    }
}
