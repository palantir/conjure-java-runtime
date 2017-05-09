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

import javax.annotation.Nullable;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;

public class ServiceException extends AbstractServiceException {

    private static final int DEFAULT_STATUS = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();

    private final ServiceExceptionLogger exceptionLogger;
    private final int status;

    public ServiceException(String messageFormat, Param<?>... messageArgs) {
        this(DEFAULT_STATUS, messageFormat,  messageArgs);
    }

    public ServiceException(Throwable cause, String messageFormat, Param<?>... messageArgs) {
        this(DEFAULT_STATUS, cause, messageFormat,  messageArgs);
    }

    public ServiceException(int status, String messageFormat, Param<?>... messageArgs) {
        this(status, null, messageFormat,  messageArgs);
    }

    public ServiceException(int status, @Nullable Throwable cause, String messageFormat,
            Param<?>... messageArgs) {
        super(ServiceExceptionLogger.format(messageFormat, messageArgs), cause);

        this.status = status;
        this.exceptionLogger = new ServiceExceptionLogger(messageFormat, messageArgs, this);
    }

    @Override
    public final void logTo(Logger log) {
        exceptionLogger.logTo(log);
    }

    /**
     * Subclasses may override this method to return custom errors to the remote caller.
     */
    @SuppressWarnings("checkstyle:designforextension")
    @Override
    public final SerializableError getError() {
        return SerializableError.of(
                "Refer to the server logs with this errorId: " + getErrorId(),
                this.getClass());
    }

    @Override
    public final int getStatus() {
        return status;
    }

}
