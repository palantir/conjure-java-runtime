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

package com.palantir.remoting3.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting3.ext.jackson.ObjectMappers;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import javax.annotation.CheckForNull;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attempts to convert a HTTP {@link Response} body as a JSON representation of a {@link SerializableError} and
 * re-create the original (or close-to) exception including exception type, message, and stacktrace. Creates {@link
 * RuntimeException} if the body cannot be interpreted as a {@link SerializableError}, or if the exception otherwise
 * fails to get re-created.
 */
public final class SerializableErrorToExceptionConverter {

    private SerializableErrorToExceptionConverter() {}

    private static final Logger log = LoggerFactory.getLogger(SerializableErrorToExceptionConverter.class);

    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();

    public static RuntimeException getException(Collection<String> contentTypes, int status,
            @CheckForNull InputStream body) {
        if (body == null) {
            return new RuntimeException(Integer.toString(status));
        }

        String bodyAsString = readBodyAsString(body);
        if (contentTypes.contains(MediaType.APPLICATION_JSON)) {
            try {
                SerializableError serializableError = MAPPER.readValue(bodyAsString, SerializableError.class);
                return new RemoteException(serializableError, status);
            } catch (Exception e) {
                String message = String.format(
                        "Error %s. Failed to parse error body and deserialize exception: %s. Body:%n%s",
                        status, e.getMessage(), bodyAsString);
                log.warn("Failed to deserialize exception: {}", message, e);
                return new RuntimeException(message);
            }
        } else {
            return new RuntimeException(String.format("Error %s. Body:%n%s", status, bodyAsString));
        }
    }

    private static String readBodyAsString(InputStream body) {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return CharStreams.toString(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
