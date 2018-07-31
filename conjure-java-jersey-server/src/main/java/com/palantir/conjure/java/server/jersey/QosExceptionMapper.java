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

import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.api.errors.QosException;
import java.time.temporal.ChronoUnit;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link ExceptionMapper} that turns {@link QosException}s into appropriate HTTP error codes and
 * headers. Three different cases are distinguished: <ol> <li>Retry any node of this service some time later: HTTP 429
 * Too Many Requests</li> <li>Retry a specific (other) node of this service: HTTP 308 Permanent Redirect + Location
 * header</li> <li>Don't retry any node of this service: HTTP 503 Unavailable</li> </ol>
 */
@Provider
final class QosExceptionMapper implements ExceptionMapper<QosException> {

    private static final Logger log = LoggerFactory.getLogger(QosExceptionMapper.class);

    @Override
    public Response toResponse(QosException qosException) {
        log.debug("Possible quality-of-service intervention", qosException);
        return qosException.accept(new QosException.Visitor<Response>() {
            @Override
            public Response visit(QosException.Throttle exception) {
                Response.ResponseBuilder response = Response.status(429);
                exception.getRetryAfter().ifPresent(duration ->
                        response.header(HttpHeaders.RETRY_AFTER, Long.toString(duration.get(ChronoUnit.SECONDS))));
                return response.build();
            }

            @Override
            public Response visit(QosException.RetryOther exception) {
                return Response
                        .status(308)
                        .header(HttpHeaders.LOCATION, exception.getRedirectTo().toString())
                        .build();
            }

            @Override
            public Response visit(QosException.Unavailable exception) {
                return Response.status(503).build();
            }
        });
    }
}
