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

package com.palantir.conjure.java.okhttp;

import com.palantir.remoting.api.errors.RemoteException;
import java.io.IOException;

// TODO(rfink): Can we hide this as an implementation detail of RemotingOkHttpCall?

/**
 * An {@link IOException} wrapper for {@link RemoteException}s. Used to make exception propagation compatible with
 * OkHttp APIs which generally require IOExceptions rather than RuntimeExceptions.
 */
public final class IoRemoteException extends IOException {
    private final RemoteException wrappedException;

    IoRemoteException(RemoteException wrappedException) {
        super(wrappedException);
        this.wrappedException = wrappedException;
    }

    public RemoteException getWrappedException() {
        return wrappedException;
    }
}
