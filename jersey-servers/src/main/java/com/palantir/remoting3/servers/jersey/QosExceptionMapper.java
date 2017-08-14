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

package com.palantir.remoting3.servers.jersey;

import com.google.common.net.HttpHeaders;
import com.palantir.remoting.api.errors.QosException;
import java.net.URL;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link ExceptionMapper} that turns http-remoting {@link QosException}s into appropriate HTTP error codes and
 * headers. Four different cases are distinguished: <ol> <li>Retry any node of this service immediately: HTTP 307
 * Temporary Redirect</li> <li>Retry any node of this service after a given backoff time: HTTP 429 Too Many Requests +
 * Retry-After header</li> <li>Retry a specific (other) node of this service immediately: HTTP 308 Permanent Redirect +
 * Location header</li> <li>Don't retry any node of this service: HTTP 503 Unavailable</li> </ol>
 */
@Provider
final class QosExceptionMapper implements ExceptionMapper<QosException> {

    private static final Logger log = LoggerFactory.getLogger(QosExceptionMapper.class);

    @Override
    public Response toResponse(QosException exception) {
        log.debug("Possible quality-of-service intervention", exception);
        if (exception instanceof QosException.Retry) {
            return retry();
        } else if (exception instanceof QosException.RetryOther) {
            QosException.RetryOther retry = (QosException.RetryOther) exception;
            return retryOther(retry.getRedirectTo());
        } else if (exception instanceof QosException.Unavailable) {
            return unavailable();
        } else {
            throw new RuntimeException(
                    "Internal error, unknown QosException subclass: " + exception.getClass().getSimpleName());
        }
    }

    private Response retry() {
        return Response.status(429).build();
    }

    private Response retryOther(URL url) {
        return Response
                .status(308)
                .header(HttpHeaders.LOCATION, url.toString())
                .build();
    }

    private Response unavailable() {
        return Response.status(503).build();
    }
}
