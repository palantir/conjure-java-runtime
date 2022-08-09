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
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * This {@link ExceptionMapper} is used when forwarding an exception from a remote server back to a calling client.
 *
 * <p>In the following call stack, where a browser calls a server A which calls another server B:
 *
 * <p>caller/browser -> [server A] -> [server B]
 *
 * <p>this is the exception mapper used in Server A. When code in B throws an exception, the {@link JsonExceptionMapper}
 * maps that exception to a JSON response with appropriate HTTP status code and returns it to server A over HTTP. That
 * response is then thrown as a {@link RemoteException} at the call point in server A. If server A does not catch this
 * exception and it raises up the call stack back into Jersey, execution enters this {@link RemoteExceptionMapper}.
 *
 * <p>Exception with HTTP status codes other than 401 and 403 are not propagated to caller to avoid an unintentional
 * dependency on the remote exception.
 */
@Provider
final class RemoteExceptionMapper extends ListenableExceptionMapper<RemoteException> {

    private static final SafeLogger log = SafeLoggerFactory.get(RemoteExceptionMapper.class);

    RemoteExceptionMapper(ConjureJerseyFeature.ExceptionListener listener) {
        super(listener);
    }

    @Override
    public Response toResponseInner(RemoteException exception) {
        if (exception.getStatus() == 401 || exception.getStatus() == 403) {
            log.info(
                    "Encountered a remote exception",
                    SafeArg.of("errorInstanceId", exception.getError().errorInstanceId()),
                    SafeArg.of("errorName", exception.getError().errorName()),
                    SafeArg.of("statusCode", exception.getStatus()),
                    exception);

            return Response.status(exception.getStatus())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(SerializableError.builder()
                            .errorCode(exception.getError().errorCode())
                            .errorName(exception.getError().errorName())
                            .errorInstanceId(exception.getError().errorInstanceId())
                            .build())
                    .build();
        } else {
            log.warn(
                    "Encountered a remote exception. Mapping to an internal error before propagating",
                    SafeArg.of("errorInstanceId", exception.getError().errorInstanceId()),
                    SafeArg.of("errorName", exception.getError().errorName()),
                    SafeArg.of("statusCode", exception.getStatus()),
                    exception);

            ServiceException propagatedException = new ServiceException(ErrorType.INTERNAL, exception);
            return Response.status(propagatedException.getErrorType().httpErrorCode())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(SerializableError.forException(propagatedException))
                    .build();
        }
    }
}
