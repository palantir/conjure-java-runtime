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

import com.palantir.conjure.java.api.errors.FieldMissingException;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
final class FieldMissingExceptionMapper extends ListenableExceptionMapper<FieldMissingException> {

    private static final SafeLogger log = SafeLoggerFactory.get(ServiceExceptionMapper.class);

    FieldMissingExceptionMapper(ConjureJerseyFeature.ExceptionListener listener) {
        super(listener);
    }

    @Override
    public Response toResponseInner(FieldMissingException exception) {
        log.info(
                "Error handling request",
                SafeArg.of("errorInstanceId", exception.getErrorInstanceId()),
                SafeArg.of("errorName", FieldMissingException.ERROR_TYPE.name()),
                exception);

        return Response.status(FieldMissingException.ERROR_TYPE.httpErrorCode())
                .type(MediaType.APPLICATION_JSON)
                .entity(exception.asSerializableError())
                .build();
    }
}
