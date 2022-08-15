/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

final class UnauthorizedException extends WebApplicationException {

    private static final ErrorType MISSING_CREDENTIALS_ERROR_TYPE =
            ErrorType.create(ErrorType.Code.UNAUTHORIZED, "Conjure:MissingCredentials");
    private static final ErrorType MALFORMED_CREDENTIALS_ERROR_TYPE =
            ErrorType.create(ErrorType.Code.UNAUTHORIZED, "Conjure:MalformedCredentials");

    private final ErrorType errorType;

    static UnauthorizedException missingCredentials() {
        return new UnauthorizedException(MISSING_CREDENTIALS_ERROR_TYPE);
    }

    static UnauthorizedException malformedCredentials(Throwable throwable) {
        return new UnauthorizedException(MALFORMED_CREDENTIALS_ERROR_TYPE, throwable);
    }

    private UnauthorizedException(ErrorType errorType) {
        super(Status.UNAUTHORIZED);
        this.errorType = errorType;
    }

    private UnauthorizedException(ErrorType errorType, Throwable throwable) {
        super(throwable, Status.UNAUTHORIZED);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }
}
