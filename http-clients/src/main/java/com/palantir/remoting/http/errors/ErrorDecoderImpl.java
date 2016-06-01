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

import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import javax.annotation.CheckForNull;

public enum ErrorDecoderImpl implements ErrorDecoder {
    SERIALIZABLE_ERROR(new ExceptionConverter() {
        @Override
        public Exception getException(Collection<String> contentTypes, int status, String reason,
                @CheckForNull InputStream body) {
            return SerializableErrorToExceptionConverter.getException(contentTypes, status, reason, body);
        }
    }),
    GO_ERROR(new ExceptionConverter() {
        @Override
        public Exception getException(Collection<String> contentTypes, int status, String reason,
                @CheckForNull InputStream body) {
            return GoErrorToExceptionConverter.getException(contentTypes, status, reason, body);
        }
    });

    private interface ExceptionConverter {
        Exception getException(Collection<String> contentTypes, int status, String reason,
                @CheckForNull InputStream body);
    }

    private final ExceptionConverter converter;

    ErrorDecoderImpl(ExceptionConverter converter) {
        this.converter = converter;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        Collection<String> contentTypes = response.headers().get(HttpHeaders.CONTENT_TYPE);
        if (contentTypes == null) {
            contentTypes = ImmutableSet.of();
        }

        InputStream body;
        try {
            body = response.body().asInputStream();
        } catch (IOException e) {
            return new RuntimeException("Cannot get input stream from response: " + e.getMessage(), e);
        }
        return this.converter.getException(contentTypes, response.status(), response.reason(), body);
    }
}
