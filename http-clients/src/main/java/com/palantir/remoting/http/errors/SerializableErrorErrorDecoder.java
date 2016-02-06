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
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.common.net.HttpHeaders;
import feign.Response;
import feign.Response.Body;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A feign {@link ErrorDecoder} that attempts to interpret the {@link Response}'s body as a JSON representation of a
 * {@link SerializableError} and re-throws the encoded exception including exception type, message, and stacktrace.
 * Throws a {@link RuntimeException} if the body cannot be interpreted as a {@link SerializableError}, or if the
 * exception fails to get re-thrown.
 */
public enum SerializableErrorErrorDecoder implements ErrorDecoder {
    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(SerializableErrorErrorDecoder.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        Collection<String> contentType = response.headers().get(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
            contentType = ImmutableSet.of();
        }

        Body body = response.body();
        if (body != null) {
            if (contentType.contains(MediaType.APPLICATION_JSON)) {
                String bodyAsString = readBodyAsString(body);

                try {
                    SerializableError error = MAPPER.readValue(bodyAsString, SerializableError.class);
                    Class<? extends Exception> exceptionClass = error.getExceptionClass();

                    // Construct remote exception
                    Exception remoteException =
                            exceptionClass.getConstructor(String.class).newInstance(error.getMessage());
                    List<StackTraceElement> stackTrace = error.getStackTrace();
                    if (stackTrace != null) {
                        remoteException.setStackTrace(stackTrace.toArray(new StackTraceElement[stackTrace.size()]));
                    }

                    // Construct local exception that wraps the remote exception
                    Exception localException;
                    try {
                        localException = exceptionClass.getConstructor(String.class, Throwable.class)
                                .newInstance(error.getMessage(), remoteException);
                    } catch (NoSuchMethodException e) {
                        localException = exceptionClass.getConstructor(String.class).newInstance(error.getMessage());
                    }
                    localException.fillInStackTrace();

                    return localException;

                } catch (Exception e) {
                    log.error("Failed to parse error body as GenericError.", e);
                    String message =
                            String.format("Error %s. Reason: %s. Failed to parse error body: %s. Body:%n%s",
                                    response.status(),
                                    response.reason(), e.getMessage(), bodyAsString);
                    return new RuntimeException(message);
                }
            } else if (contentType.contains(MediaType.TEXT_HTML) || contentType.contains(MediaType.TEXT_PLAIN)
                    || contentType.contains(MediaType.TEXT_XML)) {
                String bodyAsString = readBodyAsString(body);
                String message =
                        String.format("Error %s. Reason: %s. Body:%n%s", response.status(), response.reason(),
                                bodyAsString);
                return new RuntimeException(message);
            }

            String message = String.format("Error %s. Reason: %s. Body content type: %s", response.status(),
                    response.reason(), contentType);
            return new RuntimeException(message);
        } else {
            return new RuntimeException(String.format("%s %s", response.status(), response.reason()));
        }
    }

    /*
     * Reads the response body fully into a string so that if there are exceptions parsing the body we can at least show
     * the string representation of it.
     */
    private static String readBodyAsString(Body body) {
        try (Reader reader = body.asReader()) {
            return CharStreams.toString(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
