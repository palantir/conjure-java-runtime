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

import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

final class ServiceExceptionLogger {

    private static final String ERROR_MESSAGE_PREFIX_FORMAT = "Error handling request {}: ";

    private final String messageFormat;
    private final Param<?>[] messageArgs;
    private final AbstractServiceException exception;

    public static String format(String messageFormat, Param<?>[] messageArgs) {
        return MessageFormatter.arrayFormat(messageFormat, messageArgs).getMessage();
    }

    ServiceExceptionLogger(String messageFormat, Param<?>[] messageArgs, AbstractServiceException exception) {
        this.messageFormat = messageFormat;
        this.messageArgs = messageArgs;
        this.exception = exception;
    }

    public void logTo(Logger log) {
        log.warn(getLogMessageFormat(), getLogMessageArgs());
    }

    private String getLogMessageFormat() {
        return ERROR_MESSAGE_PREFIX_FORMAT + messageFormat;
    }

    private Object[] getLogMessageArgs() {
        Object[] args = new Object[messageArgs.length + 2];

        args[0] = SafeParam.of("errorId", exception.getErrorId());

        for (int i = 0; i < messageArgs.length; i++) {
            args[i + 1] = messageArgs[i];
        }

        args[args.length - 1] = exception;

        return args;
    }
}
