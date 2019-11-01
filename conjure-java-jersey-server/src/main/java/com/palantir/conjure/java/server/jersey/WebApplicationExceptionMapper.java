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
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.logsafe.SafeArg;
import java.util.UUID;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.server.ParamException;
import org.glassfish.jersey.server.ServerRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * While we strongly recommend that users throw {@link ServiceException}s and not {@link WebApplicationException}s,
 * these exceptions are part of the JAX-RS standard and a server such as Jersey will throw them even if user code
 * doesn't. For instance, {@link ServerRuntime} will throw {@link NotFoundException} if a request couldn't be routed,
 * and if we don't handle that in this way, then when using {@link ConjureJerseyFeature} it will get caught by {@link
 * RuntimeExceptionMapper} and transformed into a 500 internal exception, which is not what we want.
 */
@Provider
final class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    private static final Logger log = LoggerFactory.getLogger(WebApplicationExceptionMapper.class);

    @Override
    public Response toResponse(WebApplicationException exception) {
        String errorInstanceId = UUID.randomUUID().toString();
        if (exception.getResponse().getStatus() / 100 == 4 /* client error */) {
            log.info("Error handling request", SafeArg.of("errorInstanceId", errorInstanceId), exception);
        } else {
            log.error("Error handling request", SafeArg.of("errorInstanceId", errorInstanceId), exception);
        }

        if (exception instanceof ForbiddenException) {
            return JsonExceptionMapper.createResponse(ErrorType.PERMISSION_DENIED, errorInstanceId);
        } else if (exception instanceof NotFoundException) {
            return JsonExceptionMapper.createResponse(ErrorType.NOT_FOUND, errorInstanceId);
        } else if (exception instanceof BadRequestException || exception instanceof ParamException) {
            return JsonExceptionMapper.createResponse(ErrorType.INVALID_ARGUMENT, errorInstanceId);
        } else {
            return JsonExceptionMapper.createResponse(
                    exception.getResponse().getStatus(),
                    exception.getClass().getName(),
                    exception.getClass().getSimpleName(),
                    errorInstanceId);
        }
    }
}
