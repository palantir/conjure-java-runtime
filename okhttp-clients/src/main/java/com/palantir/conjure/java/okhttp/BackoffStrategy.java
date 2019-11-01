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

import java.time.Duration;
import java.util.Optional;

/** Defines a strategy for waiting in between successive retries of an operation that is subject to failure. */
public interface BackoffStrategy {
    /**
     * Returns the next suggested backoff duration, or {@link Optional#empty} if the operation should not be retried
     * again.
     */
    Optional<Duration> nextBackoff();
}
