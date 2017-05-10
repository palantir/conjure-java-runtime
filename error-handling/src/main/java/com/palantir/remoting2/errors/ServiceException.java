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
import javax.ws.rs.core.Response;
import org.slf4j.helpers.MessageFormatter;

public class ServiceException extends RuntimeException {

    private static final int DEFAULT_STATUS = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();

    private final String errorId = UUID.randomUUID().toString();
    private final String messageFormat;
    private final Param<?>[] messageParams;
    private final int status;

    public ServiceException(String messageFormat, Param<?>... messageParams) {
        this(DEFAULT_STATUS, messageFormat,  messageParams);
    }

    public ServiceException(Throwable cause, String messageFormat, Param<?>... messageParams) {
        this(DEFAULT_STATUS, cause, messageFormat,  messageParams);
    }

    public ServiceException(int status, String messageFormat, Param<?>... messageParams) {
        this(status, null, messageFormat,  messageParams);
    }

    public ServiceException(int status, @Nullable Throwable cause, String messageFormat,
            Param<?>... messageParams) {
        super(formatMessage(messageFormat, messageParams), cause);

        this.status = status;
        this.messageFormat = messageFormat;
        this.messageParams = messageParams;
    }

    /** A unique identifier for this error. */
    public final String getErrorId() {
        return errorId;
    }

    /**
     * The error that should be returned to the remote client. Subclasses may override this method to return custom
     * errors.
     */
    @SuppressWarnings("checkstyle:designforextension")
    public final SerializableError getError() {
        return SerializableError.of(
                "Refer to the server logs with this errorId: " + getErrorId(),
                this.getClass());
    }

    /** The status code for the response. */
    public final int getStatus() {
        return status;
    }

    /** The format of the exception message. */
    public final String getMessageFormat() {
        return messageFormat;
    }

    /** The parameters for the exception message. */
    public final Param<?>[] getMessageParams() {
        return messageParams;
    }

    protected static final String formatMessage(String messageFormat, Param<?>... params) {
        return MessageFormatter.arrayFormat(messageFormat, params).getMessage();
    }

}
