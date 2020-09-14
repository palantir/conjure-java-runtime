/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.server.jersey;

import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import feign.RetryableException;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

final class InternalErrorExceptionListener implements Consumer<Throwable> {
    private final JerseyServerMetrics metrics;

    InternalErrorExceptionListener(TaggedMetricRegistry registry) {
        this.metrics = JerseyServerMetrics.of(registry);
    }

    /** Records information to the singleton TaggedMetricRegistry. */
    static Consumer<Throwable> createDefault() {
        return new InternalErrorExceptionListener(SharedTaggedMetricRegistries.getSingleton());
    }

    @Override
    public void accept(Throwable exception) {
        Optional<ErrorCause> errorCause = classifyTypeOfInternalError(exception);
        if (errorCause.isPresent()) {
            ErrorCause cause = errorCause.get();
            metrics.internalerrorAll(cause.toString()).mark();
        }
    }

    /**
     * Note, this is quite a naive approach as it doesn't consider Throwable causes. See WC's implementation for a
     * more comprehensive approach.
     */
    @SuppressWarnings("CyclomaticComplexity") // switch statements are easy to read but upset checkstyle
    private static Optional<ErrorCause> classifyTypeOfInternalError(Throwable throwable) {
        if (throwable instanceof ServiceException) {
            ServiceException serviceException = (ServiceException) throwable;
            switch (serviceException.getErrorType().code()) {
                case INTERNAL:
                case TIMEOUT:
                case CUSTOM_SERVER:
                case FAILED_PRECONDITION:
                    return Optional.of(ErrorCause.SERVICE_INTERNAL);

                    /** All the below are non-5xx, so we don't consider them to be 'internal' errors. */
                case CUSTOM_CLIENT:
                case UNAUTHORIZED:
                case PERMISSION_DENIED:
                case INVALID_ARGUMENT:
                case NOT_FOUND:
                case CONFLICT:
                case REQUEST_ENTITY_TOO_LARGE:
                    return Optional.empty();
            }
            throw new SafeIllegalStateException(
                    "Unreachable",
                    SafeArg.of("code", serviceException.getErrorType().code()));
        } else if (throwable instanceof RemoteException
                || throwable instanceof RetryableException
                || throwable instanceof IOException
                || throwable instanceof UnknownRemoteException) {
            return Optional.of(ErrorCause.RPC);
        } else if (throwable instanceof RuntimeException || throwable instanceof Error) {
            return Optional.of(ErrorCause.INTERNAL);
        } else {
            return Optional.empty();
        }
    }
}
