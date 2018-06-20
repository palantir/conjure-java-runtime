/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.remoting3.okhttp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting3.ext.jackson.ObjectMappers;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import javax.ws.rs.core.MediaType;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

enum RemoteExceptionResponseHandler implements ResponseHandler<RemoteException> {
    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(RemoteExceptionResponseHandler.class);
    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();

    @Override
    public Optional<RemoteException> handle(Response response) {
        if (response.body() == null || response.body().byteStream() == null
                || response.isSuccessful()
                || response.code() == MoreHttpCodes.SWITCHING_PROTOCOLS) {
            return Optional.empty();
        }

        Collection<String> contentTypes = response.headers("Content-Type");
        if (contentTypes.contains(MediaType.APPLICATION_JSON) && !response.request().method().equals("HEAD")) {
            final String body;
            try {
                body = toString(response.body().byteStream());
            } catch (IOException e) {
                log.warn("Failed to read response body", e);
                return Optional.empty();
            }
            try {
                SerializableError serializableError = MAPPER.readValue(body, SerializableError.class);
                return Optional.of(new RemoteException(serializableError, response.code()));
            } catch (Exception e) {
                log.warn("Failed to deserialize JSON, could not deserialize SerializableError",
                        SafeArg.of("code", response.code()),
                        UnsafeArg.of("body", body),
                        e);
            }
        }

        return Optional.empty();
    }

    private static String toString(InputStream body) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return CharStreams.toString(reader);
        }
    }
}
