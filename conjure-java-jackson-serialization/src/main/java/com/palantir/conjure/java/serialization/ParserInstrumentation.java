/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.serialization;

import com.codahale.metrics.Histogram;
import com.google.common.util.concurrent.RateLimiter;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;

/** Internal utility to record instrumentation from JSON parsers. */
final class ParserInstrumentation {
    // Log at most once per second
    private static final RateLimiter LOGGING_RATE_LIMITER = RateLimiter.create(1);
    private static final SafeLogger log = SafeLoggerFactory.get(ParserInstrumentation.class);

    private final Throwable creationStackTrace;
    private final Histogram parsedStringLength;

    // Using the shared metric registry singleton to avoid API churn in methods that use this instrumentation.
    @SuppressWarnings("deprecation")
    ParserInstrumentation() {
        creationStackTrace = new SafeRuntimeException("Stream factory created here");
        parsedStringLength = JsonParserMetrics.of(SharedTaggedMetricRegistries.getSingleton())
                .stringLength();
    }

    /** Returns the input, recording length of the value. */
    String recordStringLength(String value) {
        if (value != null) {
            int length = value.length();
            // Avoid updating a histogram in the common case (small values) because this path will be exceedingly
            // hot.
            if (length > 64) {
                recordNontrivialStringLength(length);
            }
        }
        return value;
    }

    private void recordNontrivialStringLength(int length) {
        parsedStringLength.update(length);
        if (length > 1_000_000 && LOGGING_RATE_LIMITER.tryAcquire()) {
            log.info(
                    "Detected an unusually large JSON string value",
                    SafeArg.of("length", length),
                    new SafeRuntimeException("Parsed here", creationStackTrace));
        }
    }
}
