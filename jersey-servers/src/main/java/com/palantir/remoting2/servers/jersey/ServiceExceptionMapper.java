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
import com.google.common.annotations.VisibleForTesting;
import com.palantir.remoting2.errors.Param;
import com.palantir.remoting2.errors.SafeParam;
import com.palantir.remoting2.errors.SerializableError;
import com.palantir.remoting2.errors.ServiceException;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import java.util.List;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ServiceExceptionMapper implements ExceptionMapper<ServiceException> {

    private static final Logger log = LoggerFactory.getLogger(ServiceExceptionMapper.class);

    private static final String ERROR_MESSAGE_PREFIX_FORMAT = "Error handling request {}: ";
    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public Response toResponse(ServiceException exception) {
        logException(exception);

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

    private void logException(ServiceException exception) {
        log.warn(getLogMessageFormat(exception), getLogMessageParams(exception));
    }

    @VisibleForTesting
    String getLogMessageFormat(ServiceException exception) {
        return ERROR_MESSAGE_PREFIX_FORMAT + exception.getMessageFormat();
    }

    @VisibleForTesting
    Object[] getLogMessageParams(ServiceException exception) {
        List<Param<?>> messageParams = exception.getMessageParams();

        Object[] args = new Object[messageParams.size() + 2];

        args[0] = SafeParam.of("errorId", exception.getErrorId());

        for (int i = 0; i < messageParams.size(); i++) {
            args[i + 1] = messageParams.get(i);
        }

        args[args.length - 1] = exception;

        return args;
    }

}
