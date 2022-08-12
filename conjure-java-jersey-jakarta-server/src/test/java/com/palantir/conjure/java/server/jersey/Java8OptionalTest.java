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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.palantir.logsafe.Preconditions;
import com.palantir.undertest.UndertowServerExtension;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import java.io.IOException;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class Java8OptionalTest {

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
    public void testOptionalPresentWithAdditionalAccepts() throws IOException, SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/optional")
                        .addParameter("value", "val")
                        .addHeader(
                                HttpHeaders.ACCEPT, "application/x-jackson-smile, application/json, application/cbor")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(response.getFirstHeader("Content-Type").getValue())
                            .startsWith("application/json");
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("valval");
                });
    }

    @Test
    public void testOptionalComplexPresentWithAdditionalAccepts() throws IOException, SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/optional/complex")
                        .addParameter("value", "val")
                        .addHeader(
                                HttpHeaders.ACCEPT, "application/x-jackson-smile, application/json, application/cbor")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(response.getFirstHeader("Content-Type").getValue())
                            .startsWith("application/json");
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("{\"value\":\"val\"}");
                });
    }

    @Test
    public void testOptionalAbsent() {
        undertow.get("/optional", response -> {
            assertThat(response.getCode()).isEqualTo(Status.NO_CONTENT.getStatusCode());
        });
    }

    @Test
    public void testQueryParam_optionalPresent() throws NoSuchMethodException, SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/optional/string")
                        .addParameter("value", "val")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("val");
                });
    }

    @Test
    public void testQueryParam_optionalEmpty() {
        undertow.get("/optional/string", response -> {
            assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
            assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("default");
        });
    }

    @Test
    public void testQueryParam_optionalIntPresent() throws NoSuchMethodException, SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/optional/int")
                        .addParameter("value", "10")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("10");
                });
    }

    @Test
    public void testQueryParam_optionalIntEmpty() {
        undertow.get("/optional/int", response -> {
            assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
            assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("0");
        });
    }

    @Test
    public void testQueryParam_optionalIntInvalid() {
        undertow.runRequest(
                ClassicRequestBuilder.get("/optional/int")
                        .addParameter("value", "foo")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.BAD_REQUEST.getStatusCode());
                });
    }

    @Test
    public void testQueryParam_optionalDoublePresent() throws NoSuchMethodException, SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/optional/double")
                        .addParameter("value", "1.5")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("1.5");
                });
    }

    @Test
    public void testQueryParam_optionalDoubleEmpty() {
        undertow.get("/optional/double", response -> {
            assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
            assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("0.0");
        });
    }

    @Test
    public void testQueryParam_optionalDoubleInvalid() {
        undertow.runRequest(
                ClassicRequestBuilder.get("/optional/double")
                        .addParameter("value", "foo")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.BAD_REQUEST.getStatusCode());
                });
    }

    @Test
    public void testQueryParam_optionalLongPresent() throws NoSuchMethodException, SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/optional/long")
                        .addParameter("value", "100")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("100");
                });
    }

    @Test
    public void testQueryParam_optionalLongEmpty() {
        undertow.get("/optional/long", response -> {
            assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
            assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("0");
        });
    }

    @Test
    public void testQueryParam_optionalLongInvalid() {
        undertow.runRequest(
                ClassicRequestBuilder.get("/optional/long")
                        .addParameter("value", "foo")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.BAD_REQUEST.getStatusCode());
                });
    }

    public static final class OptionalTestResource implements OptionalTestService {
        @Override
        public Optional<String> getOptional(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                return Optional.empty();
            } else {
                return Optional.of(value + value);
            }
        }

        @Override
        public Optional<Complex> getOptionalComplex(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                return Optional.empty();
            } else {
                return Optional.of(new Complex(value));
            }
        }

        @Override
        public String getWithOptionalQueryParam(Optional<String> string) {
            return string.orElse("default");
        }

        @Override
        public int getWithOptionalIntQueryParam(OptionalInt value) {
            return value.orElse(0);
        }

        @Override
        public double getWithOptionalDoubleQueryParam(OptionalDouble value) {
            return value.orElse(0.0);
        }

        @Override
        public long getWithOptionalLongQueryParam(OptionalLong value) {
            return value.orElse(0L);
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface OptionalTestService {
        @GET
        @Path("/optional")
        Optional<String> getOptional(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/optional/complex")
        Optional<Complex> getOptionalComplex(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/optional/string")
        String getWithOptionalQueryParam(@QueryParam("value") Optional<String> string);

        @GET
        @Path("/optional/int")
        int getWithOptionalIntQueryParam(@QueryParam("value") OptionalInt value);

        @GET
        @Path("/optional/double")
        double getWithOptionalDoubleQueryParam(@QueryParam("value") OptionalDouble value);

        @GET
        @Path("/optional/long")
        long getWithOptionalLongQueryParam(@QueryParam("value") OptionalLong value);
    }

    public static final class Complex {

        private final String value;

        @JsonCreator
        Complex(@JsonProperty("value") String value) {
            this.value = Preconditions.checkNotNull(value, "Value is required");
        }

        @JsonGetter("value")
        public String getValue() {
            return value;
        }
    }
}
