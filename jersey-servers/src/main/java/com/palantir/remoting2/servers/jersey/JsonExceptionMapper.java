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

package com.palantir.remoting2.servers.jersey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting2.errors.SerializableError;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.StatusType;
import javax.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes out exceptions as serialized {@link SerializableError}s with {@link MediaType#APPLICATION_JSON JSON media
 * type}.
 */
abstract class JsonExceptionMapper<T extends Exception> implements ExceptionMapper<T> {

    private static final Logger log = LoggerFactory.getLogger(JsonExceptionMapper.class);

    static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final boolean includeStackTrace;

    JsonExceptionMapper(boolean includeStackTrace) {
        this.includeStackTrace = includeStackTrace;
    }

    @Override
    public final Response toResponse(T exception) {
        String exceptionMessage = Objects.toString(exception.getMessage());
        String errorId = UUID.randomUUID().toString();
        log.error("Error handling request {}", SafeArg.of("errorId", errorId), exception);

        StatusType status = this.getStatus(exception);
        ResponseBuilder builder = Response.status(status);
        try {
            final SerializableError error;
            if (includeStackTrace) {
                StackTraceElement[] stackTrace = exception.getStackTrace();
                error = SerializableError.of(exceptionMessage, exception.getClass(),
                        Arrays.asList(stackTrace));
            } else {
                error = SerializableError.of("Refer to the server logs with this errorId: "
                        + errorId, exception.getClass());
            }
            builder.type(MediaType.APPLICATION_JSON);
            String json = MAPPER.writeValueAsString(error);
            builder.entity(json);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Unable to translate exception to json for request {}", SafeArg.of("errorId", errorId), e);
            // simply write out the exception message
            builder = Response.status(status);
            builder.type(MediaType.TEXT_PLAIN);
            builder.entity(exceptionMessage);
        }
        return builder.build();
    }

    abstract StatusType getStatus(T exception);

}
