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

package com.palantir.remoting2.clients;

import com.palantir.remoting.api.errors.ErrorType;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting.api.errors.SerializableError;
import java.util.Optional;
import java.util.function.Function;
import javax.ws.rs.core.Response;

/** Utilities for reacting to {@link RemoteException}s. */
public final class RemoteExceptions {

    private RemoteExceptions() {}

    /**
     * Dispatches the given exception to appropriate handler depending on the family of error: client errors are
     * indicated by HTTP error codes in the 4xx range, and server errors are indicated by the 5xx range. All other
     * errors are dispatched as {@link ByErrorFamily.Cases#unknown}.
     * <p>
     * Build an appropriate {@code cases} handler using {@link ByErrorFamilies#cases}.
     */
    public static <T> T handleByErrorFamily(RemoteException exception, Function<ByErrorFamily, T> cases) {
        final ByErrorFamily errorCase;
        Response.Status.Family errorFamily = Response.Status.Family.familyOf(exception.getStatus());
        if (errorFamily.equals(Response.Status.Family.CLIENT_ERROR)) {
            errorCase = ByErrorFamilies.client(exception);
        } else if (errorFamily.equals(Response.Status.Family.SERVER_ERROR)) {
            errorCase = ByErrorFamilies.server(exception);
        } else {
            errorCase = ByErrorFamilies.unknown(exception);
        }
        return cases.apply(errorCase);
    }

    /**
     * Dispatches the given exception to appropriate handler depending on the {@link ErrorType.Code} of the error: if
     * the {@link SerializableError#errorCode errorCode} of the underlying {@link RemoteException#getError error}
     * corresponds to one of the known {@link ErrorType.Code}, then the corresponding case handler is invoked.
     * Otherwise, the {@link ByErrorCode.Cases#unknown} handler is invoked.
     * <p>
     * Build an appropriate {@code cases} handler using {@link ByErrorCodes#cases}.
     */
    public static <T> T handleByErrorCode(RemoteException exception, Function<ByErrorCode, T> cases) {
        String stringCode = exception.getError().errorCode();
        if (toCode(stringCode).isPresent()) {
            return cases.apply(toErrorCase(toCode(stringCode).get(), exception));
        } else {
            return cases.apply(ByErrorCodes.unknown(exception));
        }
    }

    private static Optional<ErrorType.Code> toCode(String stringCode) {
        try {
            return Optional.of(ErrorType.Code.valueOf(stringCode));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static ByErrorCode toErrorCase(ErrorType.Code code, RemoteException exception) {
        switch (code) {
            // Guaranteed to be case-complete due to errorprone check.
            case PERMISSION_DENIED:
                return ByErrorCodes.permissionDenied(exception);
            case INVALID_ARGUMENT:
                return ByErrorCodes.invalidArgument(exception);
            case FAILED_PRECONDITION:
                return ByErrorCodes.failedPrecondition(exception);
            case INTERNAL:
                return ByErrorCodes.internal(exception);
            case CUSTOM_CLIENT:
                return ByErrorCodes.customClient(exception);
            case CUSTOM_SERVER:
                return ByErrorCodes.customServer(exception);
        }

        throw new IllegalStateException("Expected to match an ErrorType.Code case, but found none: " + code);
    }
}
