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

import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import java.util.UUID;

/**
 * Writes out generic exceptions as serialized {@link SerializableError}s with {@link MediaType#APPLICATION_JSON JSON
 * media type}. Never forwards the exception message. Subclasses provide an {@link ErrorType} that is used to populate
 * the {@link SerializableError#errorCode} and {@link SerializableError#errorName()} fields as well as the HTTP response
 * status code.
 *
 * <p>Consider this call stack, where a caller/browser calls a remote method in a server:
 *
 * <p>caller/browser -> [server]
 *
 * <p>When code in the server throws an {@link Exception} that reaches Jersey, this {@link ExceptionMapper} converts
 * that exception into an HTTP {@link Response} for return to the caller/browser.
 */
abstract class JsonExceptionMapper<T extends Throwable> extends ListenableExceptionMapper<T> {

    private static final SafeLogger log = SafeLoggerFactory.get(JsonExceptionMapper.class);

    JsonExceptionMapper(ConjureJerseyFeature.ExceptionListener listener) {
        super(listener);
    }

    /** Returns the {@link ErrorType} that this exception corresponds to. */
    abstract ErrorType getErrorType(T exception);

    @Override
    public final Response toResponseInner(T exception) {
        String errorInstanceId = UUID.randomUUID().toString();
        ErrorType errorType = getErrorType(exception);

        if (errorType.httpErrorCode() / 100 == 4 /* client error */) {
            log.info(
                    "Error handling request. {}: {}",
                    SafeArg.of("errorName", errorType.name()),
                    SafeArg.of("errorInstanceId", errorInstanceId),
                    exception);
        } else {
            log.error(
                    "Error handling request. {}: {}",
                    SafeArg.of("errorName", errorType.name()),
                    SafeArg.of("errorInstanceId", errorInstanceId),
                    exception);
        }

        return createResponse(errorType, errorInstanceId);
    }

    static Response createResponse(ErrorType errorType, String errorInstanceId) {
        return createResponse(errorType.httpErrorCode(), errorType.code().name(), errorType.name(), errorInstanceId);
    }

    static Response createResponse(int httpErrorCode, String errorCode, String errorName, String errorInstanceId) {
        return Response.status(httpErrorCode)
                .type(MediaType.APPLICATION_JSON)
                .entity(SerializableError.builder()
                        .errorCode(errorCode)
                        .errorName(errorName)
                        .errorInstanceId(errorInstanceId)
                        .build())
                .build();
    }
}
