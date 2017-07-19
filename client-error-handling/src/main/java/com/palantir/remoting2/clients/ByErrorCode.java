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

import com.palantir.remoting.api.errors.RemoteException;
import org.derive4j.Data;

/**
 * Handles {@link RemoteException}s based on the {@link com.palantir.remoting.api.errors.ErrorType} produced by a
 * server. Use in conjunction with {@link RemoteExceptions#handleByErrorCode}.
 */
@Data
public abstract class ByErrorCode {

    interface Cases<T> {
        T permissionDenied(RemoteException exception);
        T invalidArgument(RemoteException exception);
        T failedPrecondition(RemoteException exception);
        T internal(RemoteException exception);
        T customClient(RemoteException exception);
        T customServer(RemoteException exception);
        T unknown(RemoteException exception);
    }

    public abstract <T> T match(Cases<T> cases);
}
