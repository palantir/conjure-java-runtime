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

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.logsafe.SafeArg;
import com.palantir.undertest.UndertowServerExtension;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class ExceptionMappingTest {

    @RegisterExtension
    public static final UndertowServerExtension undertow = UndertowServerExtension.create()
            .jersey(ConjureJerseyFeature.INSTANCE)
            .jersey(JacksonFeature.withExceptionMappers())
            .jersey(new ExceptionMappingTest.ExceptionTestResource());

    private static final Response.Status SERVER_EXCEPTION_STATUS = Status.INTERNAL_SERVER_ERROR;
    private static final Response.Status WEB_EXCEPTION_STATUS = Status.INTERNAL_SERVER_ERROR;
    private static final int REMOTE_EXCEPTION_STATUS_CODE = 400;
    private static final int UNAUTHORIZED_STATUS_CODE = 401;
    private static final int PERMISSION_DENIED_STATUS_CODE = 403;
    private static final JsonMapper MAPPER = ObjectMappers.newServerJsonMapper();

    /**
     * These tests confirm that {@link WebApplicationException}s are handled by the
     * {@link WebApplicationExceptionMapper} rather than the {@link RuntimeExceptionMapper}
     */
    @Test
    public void testForbiddenException() {
        undertow.get("/throw-forbidden-exception", response -> {
            assertThat(response.getCode()).isEqualTo(Status.FORBIDDEN.getStatusCode());
        });
    }

    @Test
    public void testNotFoundException() {
        undertow.get("/throw-not-found-exception", response -> {
            assertThat(response.getCode()).isEqualTo(Status.NOT_FOUND.getStatusCode());
        });
    }

    @Test
    public void testServerErrorException() {
        undertow.get("/throw-server-error-exception", response -> {
            assertThat(response.getCode()).isEqualTo(SERVER_EXCEPTION_STATUS.getStatusCode());
        });
    }

    @Test
    public void testWebApplicationException() {
        undertow.get("/throw-web-application-exception", response -> {
            assertThat(response.getCode()).isEqualTo(WEB_EXCEPTION_STATUS.getStatusCode());
        });
    }

    @Test
    public void testRemoteException() {
        undertow.get("/throw-remote-exception", response -> {
            assertThat(response.getCode()).isEqualTo(ErrorType.INTERNAL.httpErrorCode());

            SerializableError error = readError(response);
            assertThat(error.errorInstanceId()).isEqualTo("errorInstanceId");
            assertThat(error.errorCode()).isEqualTo(ErrorType.INTERNAL.code().toString());
            assertThat(error.errorName()).isEqualTo(ErrorType.INTERNAL.name());
            assertThat(error.parameters()).isEmpty();
        });
    }

    @Test
    public void testUnauthorizedException() {
        undertow.get("/throw-unauthorized-exception", response -> {
            assertThat(response.getCode()).isEqualTo(ErrorType.UNAUTHORIZED.httpErrorCode());

            SerializableError error = readError(response);
            assertThat(error.errorInstanceId()).isEqualTo("errorInstanceId");
            assertThat(error.errorCode()).isEqualTo("errorCode");
            assertThat(error.errorName()).isEqualTo("errorName");
            assertThat(error.parameters()).isEmpty();
        });
    }

    @Test
    public void testPermissionDeniedException() {
        undertow.get("/throw-permission-denied-exception", response -> {
            assertThat(response.getCode()).isEqualTo(ErrorType.PERMISSION_DENIED.httpErrorCode());

            SerializableError error = readError(response);
            assertThat(error.errorInstanceId()).isEqualTo("errorInstanceId");
            assertThat(error.errorCode()).isEqualTo("errorCode");
            assertThat(error.errorName()).isEqualTo("errorName");
            assertThat(error.parameters()).isEmpty();
        });
    }

    @Test
    public void testServiceException() {
        undertow.get("/throw-service-exception", response -> {
            assertThat(response.getCode()).isEqualTo(ErrorType.INVALID_ARGUMENT.httpErrorCode());

            SerializableError error = readError(response);
            assertThat(error.errorCode())
                    .isEqualTo(ErrorType.INVALID_ARGUMENT.code().toString());
            assertThat(error.errorName()).isEqualTo(ErrorType.INVALID_ARGUMENT.name());
            assertThat(error.parameters()).containsExactlyInAnyOrderEntriesOf(ImmutableMap.of("arg", "value"));
        });
    }

    @Test
    public void testQosException() {
        undertow.get("/throw-qos-retry-foo-exception", response -> {
            assertThat(response.getCode()).isEqualTo(308);
            assertThat(response.getFirstHeader("Location").getValue()).isEqualTo("http://foo");
        });
    }

    @Test
    @Disabled("These were historically mapped by Dropwizard, but aren't from normal web services")
    public void testAssertionErrorIsJsonException() {
        undertow.get("/throw-assertion-error", response -> {
            assertThat(response.getCode()).isEqualTo(SERVER_EXCEPTION_STATUS.getStatusCode());

            SerializableError error = readError(response);
            assertThat(error.errorCode()).isEqualTo(ErrorType.INTERNAL.code().toString());
            assertThat(error.errorName()).isEqualTo(ErrorType.INTERNAL.name());
        });
    }

    @Test
    public void testInvalidDefinitionExceptionIsJsonException() {
        undertow.get("/throw-invalid-definition-exception", response -> {
            assertThat(response.getCode()).isEqualTo(500);

            SerializableError error = readError(response);
            assertThat(error.errorCode()).isEqualTo(ErrorType.INTERNAL.code().toString());
            assertThat(error.errorName()).isEqualTo(ErrorType.INTERNAL.name());
        });
    }

    @Test
    public void testJsonGenerationExceptionIsJsonException() {
        undertow.get("/throw-json-generation-exception", response -> {
            assertThat(response.getCode()).isEqualTo(500);

            SerializableError error = readError(response);
            assertThat(error.errorCode()).isEqualTo(ErrorType.INTERNAL.code().toString());
            assertThat(error.errorName()).isEqualTo(ErrorType.INTERNAL.name());
        });
    }

    @Test
    public void testJsonMappingExceptionIsJsonException() {
        undertow.get("/throw-json-mapping-exception", response -> {
            assertThat(response.getCode()).isEqualTo(400);

            SerializableError error = readError(response);
            assertThat(error.errorCode())
                    .isEqualTo(ErrorType.INVALID_ARGUMENT.code().toString());
            assertThat(error.errorName()).isEqualTo(ErrorType.INVALID_ARGUMENT.name());
        });
    }

    @Test
    public void testJsonProcessingExceptionIsJsonException() {
        undertow.get("/throw-json-processing-exception", response -> {
            assertThat(response.getCode()).isEqualTo(400);

            SerializableError error = readError(response);
            assertThat(error.errorCode())
                    .isEqualTo(ErrorType.INVALID_ARGUMENT.code().toString());
            assertThat(error.errorName()).isEqualTo(ErrorType.INVALID_ARGUMENT.name());
        });
    }

    @Test
    public void testJsonParseExceptionIsJsonException() {
        undertow.get("/throw-json-parse-exception", response -> {
            assertThat(response.getCode()).isEqualTo(400);

            SerializableError error = readError(response);
            assertThat(error.errorCode())
                    .isEqualTo(ErrorType.INVALID_ARGUMENT.code().toString());
            assertThat(error.errorName()).isEqualTo(ErrorType.INVALID_ARGUMENT.name());
        });
    }

    private static SerializableError readError(CloseableHttpResponse response) throws IOException {
        return ObjectMappers.newClientObjectMapper()
                .readValue(response.getEntity().getContent(), SerializableError.class);
    }

    public static final class ExceptionTestResource implements ExceptionTestService {
        @Override
        public String throwForbiddenException() {
            throw new ForbiddenException();
        }

        @Override
        public String throwNotFoundException() {
            throw new NotFoundException();
        }

        @Override
        public String throwServerErrorException() {
            throw new ServerErrorException(SERVER_EXCEPTION_STATUS);
        }

        @Override
        public String throwWebApplicationException() {
            throw new WebApplicationException(WEB_EXCEPTION_STATUS);
        }

        @Override
        public String throwRemoteException() {
            throw new RemoteException(
                    SerializableError.builder()
                            .errorInstanceId("errorInstanceId")
                            .errorCode("errorCode")
                            .errorName("errorName")
                            .putParameters("arg", "value")
                            .build(),
                    REMOTE_EXCEPTION_STATUS_CODE);
        }

        @Override
        public String throwUnauthorizedException() {
            throw new RemoteException(
                    SerializableError.builder()
                            .errorInstanceId("errorInstanceId")
                            .errorCode("errorCode")
                            .errorName("errorName")
                            .putParameters("arg", "value")
                            .build(),
                    UNAUTHORIZED_STATUS_CODE);
        }

        @Override
        public String throwPermissionDeniedException() {
            throw new RemoteException(
                    SerializableError.builder()
                            .errorInstanceId("errorInstanceId")
                            .errorCode("errorCode")
                            .errorName("errorName")
                            .putParameters("arg", "value")
                            .build(),
                    PERMISSION_DENIED_STATUS_CODE);
        }

        @Override
        public String throwServiceException() {
            throw new ServiceException(ErrorType.INVALID_ARGUMENT, SafeArg.of("arg", "value"));
        }

        @Override
        public String throwQosRetryFooException() {
            try {
                throw QosException.retryOther(new URL("http://foo"));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String throwAssertionError() {
            throw new AssertionError();
        }

        @Override
        public String throwInvalidDefinitionException() throws IOException {
            throw InvalidDefinitionException.from(
                    MAPPER.createParser(new byte[0]), "message", MAPPER.constructType(String.class));
        }

        @Override
        public String throwJsonGenerationException() throws IOException {
            throw new JsonGenerationException("message", MAPPER.createGenerator(ByteStreams.nullOutputStream()));
        }

        @Override
        public String throwJsonMappingException() throws IOException {
            throw JsonMappingException.from(MAPPER.createParser(new byte[0]), "message");
        }

        @Override
        public String throwJsonProcessingException() throws IOException {
            throw new TestJsonProcessingException("message");
        }

        @Override
        public String throwJsonParseException() throws IOException {
            throw new JsonParseException(MAPPER.createParser(new byte[0]), "message");
        }
    }

    private static final class TestJsonProcessingException extends JsonProcessingException {
        TestJsonProcessingException(String msg) {
            super(msg);
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface ExceptionTestService {
        @GET
        @Path("/throw-forbidden-exception")
        String throwForbiddenException();

        @GET
        @Path("/throw-not-found-exception")
        String throwNotFoundException();

        @GET
        @Path("/throw-server-error-exception")
        String throwServerErrorException();

        @GET
        @Path("/throw-web-application-exception")
        String throwWebApplicationException();

        @GET
        @Path("/throw-remote-exception")
        String throwRemoteException();

        @GET
        @Path("/throw-unauthorized-exception")
        String throwUnauthorizedException();

        @GET
        @Path("/throw-permission-denied-exception")
        String throwPermissionDeniedException();

        @GET
        @Path("/throw-service-exception")
        String throwServiceException();

        @GET
        @Path("/throw-qos-retry-foo-exception")
        String throwQosRetryFooException();

        @GET
        @Path("/throw-assertion-error")
        String throwAssertionError();

        @GET
        @Path("/throw-invalid-definition-exception")
        String throwInvalidDefinitionException() throws IOException;

        @GET
        @Path("/throw-json-generation-exception")
        String throwJsonGenerationException() throws IOException;

        @GET
        @Path("/throw-json-mapping-exception")
        String throwJsonMappingException() throws IOException;

        @GET
        @Path("/throw-json-processing-exception")
        String throwJsonProcessingException() throws IOException;

        @GET
        @Path("/throw-json-parse-exception")
        String throwJsonParseException() throws IOException;
    }
}
