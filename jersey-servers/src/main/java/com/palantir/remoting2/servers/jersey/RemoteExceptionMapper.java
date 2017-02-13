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
import com.palantir.remoting2.errors.RemoteException;
import com.palantir.remoting2.errors.SerializableError;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
final class RemoteExceptionMapper implements ExceptionMapper<RemoteException> {

    private static final Logger log = LoggerFactory.getLogger(RemoteExceptionMapper.class);

    @Override
    public Response toResponse(RemoteException exception) {
        SerializableError error = exception.getRemoteException();
        Status status = Status.fromStatusCode(exception.getStatus());
        ResponseBuilder builder = Response.status(status);
        try {
            builder.type(MediaType.APPLICATION_JSON);
            String json = JsonExceptionMapper.MAPPER.writeValueAsString(error);
            builder.entity(json);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Unable to translate exception to json:", e);
            // simply write out the exception message
            builder = Response.status(status);
            builder.type(MediaType.TEXT_PLAIN);
            builder.entity(error.getMessage());
        }
        return builder.build();
    }
}
