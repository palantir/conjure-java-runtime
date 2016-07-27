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

package com.palantir.remoting1.jaxrs.feignimpl;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting1.errors.SerializableErrorToExceptionConverter;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

public enum FeignSerializableErrorErrorDecoder implements ErrorDecoder {
    INSTANCE;

    @Override
    public Exception decode(String methodKey, Response response) {
        Collection<String> contentTypes =
                HeaderAccessUtils.caseInsensitiveGet(response.headers(), HttpHeaders.CONTENT_TYPE);
        if (contentTypes == null) {
            contentTypes = ImmutableSet.of();
        }

        InputStream body;
        try {
            body = response.body().asInputStream();
        } catch (IOException e) {
            return new RuntimeException("Cannot get input stream from response: " + e.getMessage(), e);
        }
        return SerializableErrorToExceptionConverter.getException(contentTypes, response.status(), response.reason(),
                body);
    }
}
