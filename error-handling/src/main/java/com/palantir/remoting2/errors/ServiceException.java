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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.SafeLoggable;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.ws.rs.core.Response;

public class ServiceException extends RuntimeException implements SafeLoggable {

    private static final int DEFAULT_STATUS = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();

    private static final ObjectMapper MAPPER = ObjectMappers.newServerObjectMapper();

    private final String errorId = UUID.randomUUID().toString();
    private final String message;
    private final List<Arg<?>> args;
    private final int status;

    public ServiceException(String message, Arg<?>... args) {
        this(DEFAULT_STATUS, message,  args);
    }

    public ServiceException(Throwable cause, String message, Arg<?>... args) {
        this(DEFAULT_STATUS, cause, message,  args);
    }

    public ServiceException(int status, String message, Arg<?>... args) {
        this(status, null, message,  args);
    }

    public ServiceException(int status, @Nullable Throwable cause, String message,
            Arg<?>... args) {
        super(formatMessage(message, args), cause);

        this.status = status;
        this.message = message;
        this.args = ImmutableList.copyOf(args);
    }

    /** A unique identifier for this error. */
    public final String getErrorId() {
        return errorId;
    }

    /**
     * The error that should be returned to the remote client. Subclasses may override this method to return custom
     * errors.
     */
    public final SerializableError getError() {
        return SerializableError.of(
                "Refer to the server logs with this errorId: " + getErrorId(),
                this.getClass());
    }

    /** The status code for the response. */
    public final int getStatus() {
        return status;
    }

    @Override
    public final String getMessage() {
        return message;
    }

    @Override
    public final List<Arg<?>> getArgs() {
        return args;
    }

    protected static final String formatMessage(String message, Arg<?>... args) {
        if (args.length == 0) {
            return message;
        }

        String[] stringifiedArgs = Arrays.stream(args)
                .map(arg -> String.format("%s=%s", arg.getName(), toJson(arg.getValue())))
                .toArray(String[]::new);

        return String.format("%s {%s}", message, String.join(", ", stringifiedArgs));
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            return "<error serializing>";
        }
    }

}
