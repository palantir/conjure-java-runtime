/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

import com.palantir.remoting.api.errors.QosException;
import java.time.Duration;
import java.util.Optional;

class QosExceptionBackoffStrategy {

    private final BackoffStrategy delegate;

    QosExceptionBackoffStrategy(BackoffStrategy delegate) {
        this.delegate = delegate;
    }

    public final Optional<Duration> nextBackoff(QosIoException qosIoException) {
        return qosIoException.getQosException().accept(new QosException.Visitor<Optional<Duration>>() {
            @Override
            public Optional<Duration> visit(QosException.Throttle exception) {
                return exception.getRetryAfter().isPresent()
                        ? exception.getRetryAfter()
                        : delegate.nextBackoff();
            }

            @Override
            public Optional<Duration> visit(QosException.RetryOther exception) {
                throw new IllegalStateException(
                        "Internal error, did not expect to backoff for RetryOther", exception);
            }

            @Override
            public Optional<Duration> visit(QosException.Unavailable exception) {
                return delegate.nextBackoff();
            }
        });
    }
}


