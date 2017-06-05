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

import com.google.common.collect.ImmutableSet;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import java.util.Set;

public final class StatusCodeRetryableErrorDelegatingDecoder implements ErrorDecoder {

    private final Set<Integer> retryableErrors;
    private final ErrorDecoder delegate;

    public StatusCodeRetryableErrorDelegatingDecoder(Set<Integer> retryableErrors, ErrorDecoder delegate) {
        this.retryableErrors = ImmutableSet.copyOf(retryableErrors);
        this.delegate = delegate;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        Exception exception = delegate.decode(methodKey, response);

        int status = response.status();
        if (retryableErrors.contains(status)) {
            // if error code was specified as retryable, wrap exception in RetryableException
            exception = new RetryableException(String.format("Error %d", status), exception, null);
        }

        return exception;
    }

}
