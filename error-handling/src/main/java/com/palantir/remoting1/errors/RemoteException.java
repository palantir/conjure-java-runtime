/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting1.errors;

/**
 * An exception thrown to propagate a remote exception.
 */
public final class RemoteException extends RuntimeException {
    private final SerializableError remoteException;
    private final int status;

    /** Returns the exception thrown by a remote process which caused an RPC call to fail. */
    public SerializableError getRemoteException() {
        return remoteException;
    }

    /** The HTTP status code of the HTTP response conveying the remote exception. */
    public int getStatus() {
        return status;
    }

    public RemoteException(SerializableError remoteException, int status) {
        super(remoteException.getMessage());
        this.remoteException = remoteException;
        this.status = status;
    }

    @Override
    public String toString() {
        return super.toString() + remoteException.toString();
    }
}
