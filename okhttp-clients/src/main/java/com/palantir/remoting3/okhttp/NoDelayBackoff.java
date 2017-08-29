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

import java.time.Duration;
import java.util.Optional;

final class NoDelayBackoff implements BackoffStrategy {

    private final int maxNumRetries;
    private int numTimesRetried = 0;

    NoDelayBackoff(int maxNumRetries) {
        this.maxNumRetries = maxNumRetries;
    }

    @Override
    public Optional<Duration> nextBackoff() {
        numTimesRetried += 1;
        return numTimesRetried <= maxNumRetries
                ? Optional.of(Duration.ZERO)
                : Optional.empty();
    }
}
