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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting.api.errors.ErrorType;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting3.ext.jackson.ObjectMappers;
import java.util.UUID;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes out generic exceptions as serialized {@link SerializableError}s with {@link MediaType#APPLICATION_JSON JSON
 * media type}. Never forwards the exception message. Subclasses provide an {@link ErrorType} that is used to populate
 * the {@link SerializableError#errorCode} and {@link SerializableError#errorName()} fields as well as the HTTP response
 * status code.
 * <p>
 * Consider this call stack, where a caller/browser calls a remote method in a server:
 * <p>
 * caller/browser -> [server]
 * <p>
 * When code in the server throws an {@link Exception} that reaches Jersey, this {@link ExceptionMapper} converts that
 * exception into an HTTP {@link Response} for return to the caller/browser.
 */
abstract class JsonExceptionMapper<T extends Exception> implements ExceptionMapper<T> {

    private static final Logger log = LoggerFactory.getLogger(JsonExceptionMapper.class);

    static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Returns the {@link ErrorType} that this exception corresponds to. */
    abstract ErrorType getErrorType(T exception);

    @Override
    public final Response toResponse(T exception) {
        String errorInstanceId = UUID.randomUUID().toString();
        ErrorType errorType = getErrorType(exception);

        Response.Status.Family family = Response.Status.Family.familyOf(errorType.httpErrorCode());
        if (family == Response.Status.Family.CLIENT_ERROR) {
            log.info("Error handling request {}", SafeArg.of("errorInstanceId", errorInstanceId), exception);
        } else {
            log.error("Error handling request {}", SafeArg.of("errorInstanceId", errorInstanceId), exception);
        }

        return createResponse(errorType, errorInstanceId, exception.getClass().getName());
    }

    static Response createResponse(ErrorType errorType, String errorInstanceId, String exceptionClass) {
        return createResponse(errorType.httpErrorCode(), errorType.code().name(), errorType.name(),
                errorInstanceId, exceptionClass);
    }

    static Response createResponse(int httpErrorCode, String errorCode, String errorName, String errorInstanceId,
            String exceptionClass) {
        ResponseBuilder builder = Response.status(httpErrorCode);
        try {
            SerializableError error = SerializableError.builder()
                    .errorCode(errorCode)
                    .errorName(errorName)
                    .errorInstanceId(errorInstanceId)
                    .exceptionClass(exceptionClass)
                    .message("Refer to the server logs with this errorInstanceId: " + errorInstanceId)
                    .build();
            builder.type(MediaType.APPLICATION_JSON);
            String json = MAPPER.writeValueAsString(error);
            builder.entity(json);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Unable to translate exception to json for request {}",
                    SafeArg.of("errorInstanceId", errorInstanceId), e);
            builder = Response.status(httpErrorCode);
            builder.type(MediaType.TEXT_PLAIN);
            builder.entity("Unable to translate exception to json. Refer to the server logs with this errorInstanceId: "
                    + errorInstanceId);
        }
        return builder.build();
    }
}
