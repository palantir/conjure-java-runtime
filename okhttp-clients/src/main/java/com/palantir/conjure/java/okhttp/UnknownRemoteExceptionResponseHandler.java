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

package com.palantir.conjure.java.okhttp;

import com.google.common.io.CharStreams;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

enum UnknownRemoteExceptionResponseHandler implements ResponseHandler<UnknownRemoteException> {
    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(UnknownRemoteExceptionResponseHandler.class);

    @Override
    public Optional<UnknownRemoteException> handle(Response response) {
        if (response.isSuccessful() || response.code() == MoreHttpCodes.SWITCHING_PROTOCOLS) {
            return Optional.empty();
        }

        String body;
        try {
            body = response.body() != null ? toString(response.body().byteStream()) : "<empty>";
        } catch (IOException e) {
            log.warn("Failed to read response body", e);
            body = "Failed to read response body";
        }

        return Optional.of(new UnknownRemoteException(response.code(), body));
    }

    private static String toString(InputStream body) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return CharStreams.toString(reader);
        }
    }
}
