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

package com.palantir.remoting.http.errors;

import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import javax.annotation.CheckForNull;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;

public abstract class ExceptionConverter {

    protected interface JsonExceptionCreator {
        Exception getException(String bodyAsJsonString, int status, String reason);
    }

    protected static Exception getExceptionHelper(Collection<String> contentTypes, int status, String reason,
            @CheckForNull InputStream body, Logger logger, JsonExceptionCreator exceptionCreator) {
        if (body != null) {
            String bodyAsString = readBodyAsString(body);

            if (contentTypes.contains(MediaType.APPLICATION_JSON)) {
                return exceptionCreator.getException(bodyAsString, status, reason);
            } else if (contentTypes.contains(MediaType.TEXT_HTML) || contentTypes.contains(MediaType.TEXT_PLAIN)
                    || contentTypes.contains(MediaType.TEXT_XML)) {
                String message =
                        String.format("Error %s. Reason: %s. Body:%n%s", status, reason,
                                bodyAsString);
                logger.error(message);
                return new RuntimeException(message);
            }

            String message = String.format("Error %s. Reason: %s. Body content type: %s. Body as String: %s", status,
                    reason, contentTypes, bodyAsString);
            return new RuntimeException(message);
        } else {
            return new RuntimeException(String.format("%s %s", status, reason));
        }
    }

    /*
     * Reads the response body fully into a string so that if there are exceptions parsing the body we can at least show
     * the string representation of it.
     */
    protected static String readBodyAsString(InputStream body) {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return CharStreams.toString(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected ExceptionConverter() {
    }
}
