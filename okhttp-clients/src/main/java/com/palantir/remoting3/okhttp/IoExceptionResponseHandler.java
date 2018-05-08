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

import com.google.common.io.CharStreams;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIoException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import okhttp3.Response;

enum IoExceptionResponseHandler implements ResponseHandler<IOException> {
    INSTANCE;

    @Override
    public Optional<IOException> handle(Response response) {
        if (response.isSuccessful() || response.code() == MoreHttpCodes.SWITCHING_PROTOCOLS) {
            return Optional.empty();
        }

        try {
            String body = response.body() != null && response.body().byteStream() != null
                    ? toString(response.body().byteStream())
                    : "<empty>";
            return Optional.of(new SafeIoException(
                    String.format("Error %s. (Failed to parse response body as SerializableError.)", response.code()),
                    UnsafeArg.of("body", body)));
        } catch (IOException e) {
            return Optional.of(new IOException("Failed to read response body", e));
        }
    }

    private static String toString(InputStream body) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return CharStreams.toString(reader);
        }
    }
}
