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

package com.palantir.remoting3.servers.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.server.ParamException;
import org.junit.Test;

public final class WebApplicationExceptionMapperTest {

    private final WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();

    @Test
    public void testExplicitlyHandledExceptions() {
        Response response;

        response = mapper.toResponse(new ForbiddenException("secret"));
        assertThat(response.getEntity().toString()).contains("\"errorCode\" : \"PERMISSION_DENIED\"");
        assertThat(response.getEntity().toString()).contains("\"errorName\" : \"Default:PermissionDenied\"");
        assertThat(response.getEntity().toString()).contains("\"exceptionClass\" : \"javax.ws.rs.ForbiddenException\"");
        assertThat(response.getEntity().toString())
                .contains("\"message\" : \"Refer to the server logs with this errorInstanceId:");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getEntity().toString()).doesNotContain("secret");

        response = mapper.toResponse(new NotFoundException());
        assertThat(response.getEntity().toString()).contains("\"errorCode\" : \"NOT_FOUND\"");
        assertThat(response.getEntity().toString()).contains("\"errorName\" : \"Default:NotFound\"");
        assertThat(response.getEntity().toString()).contains("\"exceptionClass\" : \"javax.ws.rs.NotFoundException\"");
        assertThat(response.getEntity().toString())
                .contains("\"message\" : \"Refer to the server logs with this errorInstanceId:");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getEntity().toString()).doesNotContain("secret");

        response = mapper.toResponse(new BadRequestException());
        assertThat(response.getEntity().toString()).contains("\"errorCode\" : \"INVALID_ARGUMENT\"");
        assertThat(response.getEntity().toString()).contains("\"errorName\" : \"Default:InvalidArgument\"");
        assertThat(response.getEntity().toString())
                .contains("\"exceptionClass\" : \"javax.ws.rs.BadRequestException\"");
        assertThat(response.getEntity().toString())
                .contains("\"message\" : \"Refer to the server logs with this errorInstanceId:");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).doesNotContain("secret");

        response = mapper.toResponse(new ParamException.CookieParamException(null, null, null));
        assertThat(response.getEntity().toString()).contains("\"errorCode\" : \"INVALID_ARGUMENT\"");
        assertThat(response.getEntity().toString()).contains("\"errorName\" : \"Default:InvalidArgument\"");
        assertThat(response.getEntity().toString())
                .contains("\"exceptionClass\" : \"org.glassfish.jersey.server.ParamException$CookieParamException\"");
        assertThat(response.getEntity().toString())
                .contains("\"message\" : \"Refer to the server logs with this errorInstanceId:");
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getEntity().toString()).doesNotContain("secret");
    }

    @Test
    public void testNotExplicitlyHandledExceptions() {
        Response response;

        response = mapper.toResponse(new WebApplicationException("secret", 503));
        assertThat(response.getEntity().toString()).contains("\"errorCode\" : \"javax.ws.rs.WebApplicationException\"");
        assertThat(response.getEntity().toString()).contains("\"errorName\" : \"WebApplicationException\"");
        assertThat(response.getEntity().toString())
                .contains("\"exceptionClass\" : \"javax.ws.rs.WebApplicationException\"");
        assertThat(response.getEntity().toString())
                .contains("\"message\" : \"Refer to the server logs with this errorInstanceId:");
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getEntity().toString()).doesNotContain("secret");

        response = mapper.toResponse(new ServiceUnavailableException("secret"));
        assertThat(response.getEntity().toString())
                .contains("\"errorCode\" : \"javax.ws.rs.ServiceUnavailableException\"");
        assertThat(response.getEntity().toString()).contains("\"errorName\" : \"ServiceUnavailableException\"");
        assertThat(response.getEntity().toString())
                .contains("\"exceptionClass\" : \"javax.ws.rs.ServiceUnavailableException\"");
        assertThat(response.getEntity().toString())
                .contains("\"message\" : \"Refer to the server logs with this errorInstanceId:");
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getEntity().toString()).doesNotContain("secret");
    }
}
