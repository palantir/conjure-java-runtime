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

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.palantir.conjure.java.serialization.ObjectMappers;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.RedirectionException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ParamException;
import org.junit.jupiter.api.Test;

public final class WebApplicationExceptionMapperTest {

    private final WebApplicationExceptionMapper mapper =
            new WebApplicationExceptionMapper(ConjureJerseyFeature.NoOpListener.INSTANCE);
    private final JsonMapper objectMapper = ObjectMappers.newServerJsonMapper()
            .rebuild()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Test
    public void testExplicitlyHandledExceptions() throws Exception {
        Response response = mapper.toResponse(new NotAuthorizedException("secret"));
        String entity = objectMapper.writeValueAsString(response.getEntity());
        assertThat(entity).contains("\"errorCode\" : \"UNAUTHORIZED\"");
        assertThat(entity).contains("\"errorName\" : \"Default:Unauthorized\"");
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(entity).doesNotContain("secret");

        response = mapper.toResponse(UnauthorizedException.missingCredentials());
        entity = objectMapper.writeValueAsString(response.getEntity());
        assertThat(entity).contains("\"errorCode\" : \"UNAUTHORIZED\"");
        assertThat(entity).contains("\"errorName\" : \"Conjure:MissingCredentials\"");
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(entity).doesNotContain("secret");

        response = mapper.toResponse(new ForbiddenException("secret"));
        entity = objectMapper.writeValueAsString(response.getEntity());
        assertThat(entity).contains("\"errorCode\" : \"PERMISSION_DENIED\"");
        assertThat(entity).contains("\"errorName\" : \"Default:PermissionDenied\"");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(entity).doesNotContain("secret");

        response = mapper.toResponse(new NotFoundException("secret"));
        entity = objectMapper.writeValueAsString(response.getEntity());
        assertThat(entity).contains("\"errorCode\" : \"NOT_FOUND\"");
        assertThat(entity).contains("\"errorName\" : \"Default:NotFound\"");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(entity).doesNotContain("secret");

        response = mapper.toResponse(new BadRequestException("secret"));
        entity = objectMapper.writeValueAsString(response.getEntity());
        assertThat(entity).contains("\"errorCode\" : \"INVALID_ARGUMENT\"");
        assertThat(entity).contains("\"errorName\" : \"Default:InvalidArgument\"");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(entity).doesNotContain("secret");

        response = mapper.toResponse(new ParamException.CookieParamException(null, "secret", "secret"));
        entity = objectMapper.writeValueAsString(response.getEntity());
        assertThat(entity).contains("\"errorCode\" : \"INVALID_ARGUMENT\"");
        assertThat(entity).contains("\"errorName\" : \"Default:InvalidArgument\"");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(entity).doesNotContain("secret");
    }

    @Test
    public void testNotExplicitlyHandledExceptions() throws Exception {
        Response response;

        response = mapper.toResponse(new WebApplicationException("secret", 503));
        String entity = objectMapper.writeValueAsString(response.getEntity());
        assertThat(entity).contains("\"errorCode\" : \"jakarta.ws.rs.WebApplicationException\"");
        assertThat(entity).contains("\"errorName\" : \"WebApplicationException\"");
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(entity).doesNotContain("secret");

        response = mapper.toResponse(new ServiceUnavailableException("secret"));
        entity = objectMapper.writeValueAsString(response.getEntity());
        assertThat(entity).contains("\"errorCode\" : \"jakarta.ws.rs.ServiceUnavailableException\"");
        assertThat(entity).contains("\"errorName\" : \"ServiceUnavailableException\"");
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(entity).doesNotContain("secret");
    }

    @Test
    public void handle304NotModified() throws Exception {
        Response response = mapper.toResponse(
                new RedirectionException(Response.notModified("test-etag").build()));
        String entity = objectMapper.writeValueAsString(response.getEntity());
        assertThat(entity).contains("\"errorCode\" : \"jakarta.ws.rs.RedirectionException\"");
        assertThat(entity).contains("\"errorName\" : \"RedirectionException\"");
        assertThat(entity).contains("\"errorInstanceId\" : ");
        assertThat(response.getStatus()).isEqualTo(304);
        assertThat(response.getHeaderString("ETag")).isNull();
        assertThat(entity).doesNotContain("secret");
    }
}
