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

package com.palantir.remoting3.servers.jersey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting.api.errors.ServiceException;
import com.palantir.remoting3.ext.jackson.ObjectMappers;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serializes {@link ServiceException}s into a JSON representation of a {@link SerializableError}s (see {@link
 * SerializableError#forException}). Also see {@link JsonExceptionMapper} for more information on the interplay of
 * Jersey and exception mappers.
 */
final class ServiceExceptionMapper implements ExceptionMapper<ServiceException> {

    private static final Logger log = LoggerFactory.getLogger(ServiceExceptionMapper.class);
    private static final ObjectMapper MAPPER =
            ObjectMappers.newServerObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public Response toResponse(ServiceException exception) {
        int httpStatus = exception.getErrorType().httpErrorCode();
        if (httpStatus / 100 == 4 /* client error */) {
            log.info("Error handling request",
                    SafeArg.of("errorInstanceId", exception.getErrorInstanceId()), exception);
        } else {
            log.error("Error handling request",
                    SafeArg.of("errorInstanceId", exception.getErrorInstanceId()), exception);
        }

        ResponseBuilder builder = Response.status(httpStatus);
        try {
            // We explicitly set the message to a more UI- and backcompat-friendly string that includes the error ID.
            SerializableError error = SerializableError.builder()
                    .from(SerializableError.forException(exception))
                    .message("Refer to the server logs with this errorInstanceId: " + exception.getErrorInstanceId())
                    .build();
            builder.type(MediaType.APPLICATION_JSON);
            String json = MAPPER.writeValueAsString(error);
            builder.entity(json);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Unable to translate exception to json",
                    SafeArg.of("errorInstanceId", exception.getErrorInstanceId()), e);
            // simply write out the exception message
            builder.type(MediaType.TEXT_PLAIN);
            builder.entity("Unable to translate exception to json. Refer to the server logs with this errorInstanceId: "
                    + exception.getErrorInstanceId());
        }
        return builder.build();
    }
}
