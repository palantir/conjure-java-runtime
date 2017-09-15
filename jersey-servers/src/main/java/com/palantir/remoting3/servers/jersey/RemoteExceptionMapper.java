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
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting.api.errors.SerializableError;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This {@link ExceptionMapper} is used when forwarding an exception from a remote server back to a calling client.
 * <p>
 * In the following call stack, where a browser calls a server A which calls another server B:
 * <p>
 * caller/browser -> [server A] -> [server B]
 * <p>
 * this is the exception mapper used in Server A. When code in B throws an exception, the {@link JsonExceptionMapper}
 * maps that exception to a JSON response with appropriate HTTP status code and returns it to server A over HTTP.
 * That response is then processed by a {@link com.palantir.remoting3.errors.SerializableErrorToExceptionConverter} and
 * thrown as a {@link RemoteException} at the call point in server A.  If server A does not catch this exception and
 * it raises up the call stack back into Jersey, execution enters this {@link RemoteExceptionMapper}.
 * <p>
 * To preserve debuggability, the exception and HTTP status code from B's exception are logged at WARN level and
 * returned back to the caller/browser.
 */
@Provider
final class RemoteExceptionMapper implements ExceptionMapper<RemoteException> {

    private static final Logger log = LoggerFactory.getLogger(RemoteExceptionMapper.class);

    @Override
    public Response toResponse(RemoteException exception) {
        Status status = Status.fromStatusCode(exception.getStatus());

        // log at WARN instead of ERROR because although this indicates an issue in a remote server, it is not
        log.warn("Forwarding response and status code from remote server back to caller",
                SafeArg.of("statusCode", status.getStatusCode()),
                exception);

        SerializableError error = exception.getError();
        ResponseBuilder builder = Response.status(status);
        try {
            builder.type(MediaType.APPLICATION_JSON);
            String json = JsonExceptionMapper.MAPPER.writeValueAsString(error);
            builder.entity(json);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Unable to translate exception to json for request", e);
            // simply write out the exception message
            builder = Response.status(status);
            builder.type(MediaType.TEXT_PLAIN);
            builder.entity(error.errorCode() + ": " + error.errorName());
        }
        return builder.build();
    }
}
