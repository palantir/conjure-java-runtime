/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.http.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attempts to convert a HTTP {@link Response} body as a JSON representation of a {@link SerializableError} and
 * re-create the original (or close-to) exception including exception type, message, and stacktrace. Creates
 * {@link RuntimeException} if the body cannot be interpreted as a {@link SerializableError}, or if the exception
 * otherwise fails to get re-created.
 */
public final class SerializableErrorToExceptionConverter extends ExceptionConverter {

    private SerializableErrorToExceptionConverter() {
        // util
    }

    private static final Logger log = LoggerFactory.getLogger(SerializableErrorToExceptionConverter.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Exception getException(Collection<String> contentTypes, int status, String reason,
            @CheckForNull InputStream body) {
        return getExceptionHelper(contentTypes, status, reason, body, log, new JsonExceptionCreator() {
            @Override
            public Exception getException(String bodyAsJsonString, int status, String reason) {
                SerializableError error;
                try {
                    error = MAPPER.readValue(bodyAsJsonString, SerializableError.class);
                } catch (Exception e) {
                    String message = String.format(
                            "Error %s. Reason: %s. Failed to parse error body and instantiate exception: %s. Body:%n%s",
                            status, reason, e.getMessage(), bodyAsJsonString);
                    log.error(message, e);
                    return new RuntimeException(message);
                }

                // Construct remote exception and fill with remote stacktrace
                Exception remoteException = constructException(error.getExceptionClassName(), error.getMessage(),
                        status, null);
                List<StackTraceElement> stackTrace = error.getStackTrace();
                if (stackTrace != null) {
                    remoteException.setStackTrace(stackTrace.toArray(new StackTraceElement[stackTrace.size()]));
                }

                // Construct local exception that wraps the remote exception and fill with stack trace of local
                // call (yet without the reflection overhead).
                Exception localException =
                        constructException(error.getExceptionClassName(), error.getMessage(), status,
                                remoteException);
                localException.fillInStackTrace();

                return localException;
            }
        });
    }

    // wrappedException may be null to indicate an unknown cause
    @SuppressWarnings("unchecked")
    private static Exception constructException(String exceptionClassName, String message, int status,
            @CheckForNull Throwable wrappedException) {
        Class<? extends Exception> exceptionClass;
        try {
            exceptionClass = (Class<? extends Exception>) Class.forName(exceptionClassName);
        } catch (ClassNotFoundException e) {
            // use the most expressive constructor that exists in Jersey 1.x
            return new WebApplicationException(wrappedException, status);
        }

        switch (exceptionClassName) {
            case "javax.ws.rs.ClientErrorException":
            case "javax.ws.rs.ServerErrorException":
                try {
                    return exceptionClass.getConstructor(String.class, int.class, Throwable.class)
                            .newInstance(message, status, wrappedException);
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    // use the most expressive constructor that exists in Jersey 1.x
                    return new WebApplicationException(wrappedException, status);
                }

            case "javax.ws.rs.WebApplicationException":
                try {
                    return exceptionClass.getConstructor(String.class, Throwable.class, int.class)
                            .newInstance(message, wrappedException, status);
                } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    // use the most expressive constructor that exists in Jersey 1.x
                    return new WebApplicationException(wrappedException, status);
                }

            default:
                // Note: If another constructor is added, then we should refactor the construction logic in order to
                // avoid nested try/catch
                try {
                    return exceptionClass.getConstructor(String.class, Throwable.class)
                            .newInstance(message, wrappedException);
                } catch (NoSuchMethodException | InstantiationException
                        | IllegalAccessException | InvocationTargetException e) {
                    try {
                        return exceptionClass.getConstructor(String.class).newInstance(message);
                    } catch (Exception e1) {
                        return new RuntimeException(String.format(
                                "Failed to construct exception as %s, constructing RuntimeException instead: %s%n%s",
                                exceptionClass.toString(), e1.toString(), message),
                                wrappedException);
                    }
                }
        }
    }

}
