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

package com.palantir.remoting.retrofit.errors;

import com.google.common.collect.ImmutableSet;
import com.palantir.remoting.http.errors.SerializableErrorToExceptionConverter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import retrofit.ErrorHandler;
import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.Response;

public enum RetrofitSerializableErrorErrorHandler implements ErrorHandler {
    INSTANCE;

    @Override
    public Throwable handleError(RetrofitError cause) {
        Response response = cause.getResponse();

        Collection<String> contentTypes = ImmutableSet.of();
        for (Header header : response.getHeaders()) {
            if (header.getName().equals("Content-Type")) {
                contentTypes = ImmutableSet.of(header.getValue());
                break;
            }
        }

        InputStream body;
        try {
            body = response.getBody().in();
        } catch (IOException e) {
            return new RuntimeException("Cannot get input stream from response: " + e.getMessage(), e);
        }
        return SerializableErrorToExceptionConverter.getException(contentTypes, response.getStatus(),
                response.getReason(), body);
    }

}
