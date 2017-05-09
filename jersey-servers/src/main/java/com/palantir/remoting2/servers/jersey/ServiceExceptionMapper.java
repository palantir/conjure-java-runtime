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

package com.palantir.remoting2.servers.jersey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.palantir.remoting2.errors.AbstractServiceException;
import com.palantir.remoting2.errors.SerializableError;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ServiceExceptionMapper implements ExceptionMapper<AbstractServiceException> {

    private static final Logger log = LoggerFactory.getLogger(JsonExceptionMapper.class);

    static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public Response toResponse(AbstractServiceException exception) {
        exception.logTo(log);

        int status = exception.getStatus();
        Response.ResponseBuilder builder = Response.status(status);

        try {
            SerializableError error = exception.getError();
            builder.type(MediaType.APPLICATION_JSON);
            String json = MAPPER.writeValueAsString(error);
            builder.entity(json);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Unable to translate exception to json for request {}", exception.getErrorId(), e);

            builder = Response.status(status);
            builder.type(MediaType.TEXT_PLAIN);
            builder.entity("Refer to the server logs with this errorId: " + exception.getErrorId());
        }

        return builder.build();
    }

}
