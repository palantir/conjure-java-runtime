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

package com.palantir.remoting2.errors;

import java.util.UUID;
import javax.annotation.Nullable;
import javax.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;

/**
 * A base class for defining customized exceptions.
 */
public abstract class ServiceException extends RuntimeException {

    private final String errorId = UUID.randomUUID().toString();

    protected ServiceException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    /** Logs this exception. This method is called by the corresponding {@link ExceptionMapper}. */
    public abstract void logTo(Logger log);

    /** The error that should be returned to the remote client. */
    public abstract SerializableError getError();

    /** The status code for the response. */
    public abstract int getStatus();

    /** A unique identifier for this error. */
    public final String getErrorId() {
        return errorId;
    }

}
