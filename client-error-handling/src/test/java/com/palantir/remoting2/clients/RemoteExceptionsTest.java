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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.remoting.api.errors.ErrorType;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting.api.errors.ServiceException;
import java.util.function.Function;
import org.junit.Test;

public final class RemoteExceptionsTest {

    private static final RemoteException PERMISSION_DENIED = createError(ErrorType.PERMISSION_DENIED);
    private static final RemoteException INVALID_ARGUMENT = createError(ErrorType.INVALID_ARGUMENT);
    private static final RemoteException FAILED_PRECONDITION = createError(ErrorType.FAILED_PRECONDITION);
    private static final RemoteException INTERNAL = createError(ErrorType.INTERNAL);
    private static final RemoteException CUSTOM_CLIENT = createError(ErrorType.client("Client"));
    private static final RemoteException CUSTOM_SERVER = createError(ErrorType.server("Server"));

    private static final RemoteException UNKNOWN = new RemoteException(SerializableError.builder()
            .errorCode("code")  // cannot be mapped to ErrorCode
            .errorName("name")
            .errorInstanceId("id")
            .build(),
            204  // cannot be mapped to client or server error family
    );

    // Fake handlers that return the expected error for a given case.
    private static final Function<ByErrorFamily, RemoteException> BY_FAMILY_HANDLER =
            ByErrorFamilies.cases()
                    .client(e -> CUSTOM_CLIENT)
                    .server(e -> CUSTOM_SERVER)
                    .unknown(e -> UNKNOWN);

    private static final Function<ByErrorCode, RemoteException> BY_CODE_HANDLER =
            ByErrorCodes.cases()
                    .permissionDenied(e -> PERMISSION_DENIED)
                    .invalidArgument(e -> INVALID_ARGUMENT)
                    .failedPrecondition(e -> FAILED_PRECONDITION)
                    .internal(e -> INTERNAL)
                    .customClient(e -> CUSTOM_CLIENT)
                    .customServer(e -> CUSTOM_SERVER)
                    .unknown(e -> UNKNOWN);


    @Test
    public void testDispatching_errorFamily() throws Exception {
        assertThat(RemoteExceptions.handleByErrorFamily(CUSTOM_CLIENT, BY_FAMILY_HANDLER)).isEqualTo(CUSTOM_CLIENT);
        assertThat(RemoteExceptions.handleByErrorFamily(CUSTOM_SERVER, BY_FAMILY_HANDLER)).isEqualTo(CUSTOM_SERVER);
        assertThat(RemoteExceptions.handleByErrorFamily(UNKNOWN, BY_FAMILY_HANDLER)).isEqualTo(UNKNOWN);
    }

    @Test
    public void testDispatching_errorCode() throws Exception {
        assertThat(RemoteExceptions.handleByErrorCode(PERMISSION_DENIED, BY_CODE_HANDLER)).isEqualTo(PERMISSION_DENIED);
        assertThat(RemoteExceptions.handleByErrorCode(INVALID_ARGUMENT, BY_CODE_HANDLER)).isEqualTo(INVALID_ARGUMENT);
        assertThat(RemoteExceptions.handleByErrorCode(FAILED_PRECONDITION, BY_CODE_HANDLER))
                .isEqualTo(FAILED_PRECONDITION);
        assertThat(RemoteExceptions.handleByErrorCode(INTERNAL, BY_CODE_HANDLER)).isEqualTo(INTERNAL);
        assertThat(RemoteExceptions.handleByErrorCode(CUSTOM_CLIENT, BY_CODE_HANDLER)).isEqualTo(CUSTOM_CLIENT);
        assertThat(RemoteExceptions.handleByErrorCode(CUSTOM_SERVER, BY_CODE_HANDLER)).isEqualTo(CUSTOM_SERVER);
        assertThat(RemoteExceptions.handleByErrorCode(UNKNOWN, BY_CODE_HANDLER)).isEqualTo(UNKNOWN);
    }

    private static RemoteException createError(ErrorType type) {
        return new RemoteException(SerializableError.forException(new ServiceException(type)), type.httpErrorCode());
    }
}
