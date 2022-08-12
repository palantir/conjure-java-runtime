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

import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.tokens.auth.AuthHeader;
import com.palantir.tokens.auth.BearerToken;
import com.palantir.undertest.UndertowServerExtension;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class AuthTest {

    @RegisterExtension
    public static final UndertowServerExtension undertow = UndertowServerExtension.create()
            .jersey(ConjureJerseyFeature.INSTANCE)
            .jersey(new AuthTestResource());

    @Test
    public void testAuthHeader() {
        undertow.runRequest(
                ClassicRequestBuilder.get("/authHeader")
                        .addHeader("Authorization", "Bearer bearerToken")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("Bearer bearerToken");
                });
    }

    @Test
    public void testAuthHeaderNullable_present() throws SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/authHeaderNullable")
                        .addHeader("Authorization", "Bearer bearerToken")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8))
                            .isEqualTo("Bearer bearerToken");
                });
    }

    @Test
    public void testAuthHeaderNullable_absent() throws SecurityException {
        undertow.runRequest(ClassicRequestBuilder.get("/authHeaderNullable").build(), response -> {
            assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
            assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("[no value]");
        });
    }

    @Test
    public void testAuthHeader_missingCredentials() throws SecurityException {
        undertow.runRequest(new HttpGet("/authHeader"), response -> {
            assertThat(response.getCode()).isEqualTo(Status.UNAUTHORIZED.getStatusCode());

            SerializableError error = ObjectMappers.newClientObjectMapper()
                    .readValue(response.getEntity().getContent(), SerializableError.class);
            assertThat(error.errorCode())
                    .isEqualTo(ErrorType.UNAUTHORIZED.code().toString());
            assertThat(error.errorName()).isEqualTo("Conjure:MissingCredentials");
            assertThat(error.parameters()).isEmpty();
        });
    }

    @Test
    public void testAuthHeader_malformedCredentials() throws SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/authHeader")
                        .addHeader("Authorization", "!")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.UNAUTHORIZED.getStatusCode());

                    SerializableError error = ObjectMappers.newClientObjectMapper()
                            .readValue(response.getEntity().getContent(), SerializableError.class);
                    assertThat(error.errorCode())
                            .isEqualTo(ErrorType.UNAUTHORIZED.code().toString());
                    assertThat(error.errorName()).isEqualTo("Conjure:MalformedCredentials");
                    assertThat(error.parameters()).isEmpty();
                });
    }

    @Test
    public void testAuthHeaderOtherHeader_illegalArgument() throws SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/authHeaderOtherHeader")
                        .setHeader("Other", "!")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.BAD_REQUEST.getStatusCode());

                    SerializableError error = ObjectMappers.newClientObjectMapper()
                            .readValue(response.getEntity().getContent(), SerializableError.class);
                    assertThat(error.errorCode())
                            .isEqualTo(ErrorType.INVALID_ARGUMENT.code().toString());
                    assertThat(error.errorName()).isEqualTo("Default:InvalidArgument");
                    assertThat(error.parameters()).isEmpty();
                });
    }

    @Test
    public void testAuthHeaderOtherParam_illegalArgument() throws SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/authHeaderOtherParam")
                        .addParameter("Authorization", "!")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.BAD_REQUEST.getStatusCode());

                    SerializableError error = ObjectMappers.newClientObjectMapper()
                            .readValue(response.getEntity().getContent(), SerializableError.class);
                    assertThat(error.errorCode())
                            .isEqualTo(ErrorType.INVALID_ARGUMENT.code().toString());
                    assertThat(error.errorName()).isEqualTo("Default:InvalidArgument");
                    assertThat(error.parameters()).isEmpty();
                });
    }

    @Test
    public void testBearerToken() throws SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/bearerToken")
                        .addHeader("Cookie", "PALANTIR_TOKEN=bearerToken")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("bearerToken");
                });
    }

    @Test
    public void testBearerTokenNullable_present() {
        undertow.runRequest(
                ClassicRequestBuilder.get("/bearerTokenNullable")
                        .addHeader("Cookie", "PALANTIR_TOKEN=bearerToken")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("bearerToken");
                });
    }

    @Test
    public void testBearerTokenNullable_absent() {
        undertow.runRequest(ClassicRequestBuilder.get("/bearerTokenNullable").build(), response -> {
            assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
            assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("[no value]");
        });
    }

    @Test
    public void testBearerToken_missingCredentials() throws SecurityException {
        undertow.runRequest(new HttpGet("/bearerToken"), response -> {
            assertThat(response.getCode()).isEqualTo(Status.UNAUTHORIZED.getStatusCode());

            SerializableError error = ObjectMappers.newClientObjectMapper()
                    .readValue(response.getEntity().getContent(), SerializableError.class);
            assertThat(error.errorCode())
                    .isEqualTo(ErrorType.UNAUTHORIZED.code().toString());
            assertThat(error.errorName()).isEqualTo("Conjure:MissingCredentials");
            assertThat(error.parameters()).isEmpty();
        });
    }

    @Test
    public void testBearerToken_malformedCredentials() throws SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/bearerToken")
                        .addHeader("Cookie", "PALANTIR_TOKEN=!")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.UNAUTHORIZED.getStatusCode());

                    SerializableError error = ObjectMappers.newClientObjectMapper()
                            .readValue(response.getEntity().getContent(), SerializableError.class);
                    assertThat(error.errorCode())
                            .isEqualTo(ErrorType.UNAUTHORIZED.code().toString());
                    assertThat(error.errorName()).isEqualTo("Conjure:MalformedCredentials");
                    assertThat(error.parameters()).isEmpty();
                });
    }

    @Test
    public void testBearerTokenOtherParam_illegalArgument() throws SecurityException {
        undertow.runRequest(
                ClassicRequestBuilder.get("/bearerTokenOtherParam")
                        .addParameter("PALANTIR_TOKEN", "!")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.BAD_REQUEST.getStatusCode());

                    SerializableError error = ObjectMappers.newClientObjectMapper()
                            .readValue(response.getEntity().getContent(), SerializableError.class);
                    assertThat(error.errorCode())
                            .isEqualTo(ErrorType.INVALID_ARGUMENT.code().toString());
                    assertThat(error.errorName()).isEqualTo("Default:InvalidArgument");
                    assertThat(error.parameters()).isEmpty();
                });
    }

    public static final class AuthTestResource implements AuthTestService {
        @Override
        public String getAuthHeader(AuthHeader value) {
            return value.toString();
        }

        @Override
        public String getAuthHeaderNullable(@Nullable AuthHeader value) {
            return value == null ? "[no value]" : value.toString();
        }

        @Override
        public String getAuthHeaderOtherHeader(AuthHeader value) {
            return value.toString();
        }

        @Override
        public String getAuthHeaderOtherParam(AuthHeader value) {
            return value.toString();
        }

        @Override
        public String getBearerToken(BearerToken value) {
            return value.toString();
        }

        @Override
        public String getBearerTokenNullable(@Nullable BearerToken value) {
            return value == null ? "[no value]" : value.toString();
        }

        @Override
        public String getBearerTokenOtherParam(BearerToken value) {
            return value.toString();
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface AuthTestService {
        @GET
        @Path("/authHeader")
        String getAuthHeader(@HeaderParam("Authorization") AuthHeader value);

        @GET
        @Path("/authHeaderNullable")
        String getAuthHeaderNullable(@Nullable @HeaderParam("Authorization") AuthHeader value);

        @GET
        @Path("/authHeaderOtherHeader")
        String getAuthHeaderOtherHeader(@HeaderParam("Other") AuthHeader value);

        @GET
        @Path("/authHeaderOtherParam")
        String getAuthHeaderOtherParam(@QueryParam("Authorization") AuthHeader value);

        @GET
        @Path("/bearerToken")
        String getBearerToken(@CookieParam("PALANTIR_TOKEN") BearerToken value);

        @GET
        @Path("/bearerTokenNullable")
        String getBearerTokenNullable(@Nullable @CookieParam("PALANTIR_TOKEN") BearerToken value);

        @GET
        @Path("/bearerTokenOtherParam")
        String getBearerTokenOtherParam(@QueryParam("PALANTIR_TOKEN") BearerToken value);
    }
}
