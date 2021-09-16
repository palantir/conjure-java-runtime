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
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.util.Objects;
import java.util.UUID;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
final class SafeIllegalArgumentExceptionMapper extends ListenableExceptionMapper<SafeIllegalArgumentException> {

    private static final SafeLogger log = SafeLoggerFactory.get(SafeIllegalArgumentExceptionMapper.class);

    SafeIllegalArgumentExceptionMapper(ConjureJerseyFeature.ExceptionListener listener) {
        super(listener);
    }

    @Override
    public Response toResponseInner(SafeIllegalArgumentException exception) {
        String errorInstanceId = UUID.randomUUID().toString();
        ErrorType errorType = ErrorType.INVALID_ARGUMENT;
        log.info(
                "Constant",
                SafeArg.of("safeMessage", exception.getLogMessage()),
                SafeArg.of("errorInstanceId", errorInstanceId),
                SafeArg.of("errorName", errorType.name()),
                exception);
        SerializableError.Builder builder = new SerializableError.Builder()
                .errorCode(errorType.code().name())
                .errorName(errorType.name())
                .errorInstanceId(errorInstanceId);

        for (Arg<?> arg : exception.getArgs()) {
            builder.putParameters(arg.getName(), Objects.toString(arg.getValue()));
        }
        builder.putParameters("safeMessage", exception.getLogMessage());

        return Response.status(errorType.httpErrorCode())
                .type(MediaType.APPLICATION_JSON)
                .entity(builder.build())
                .build();
    }
}
