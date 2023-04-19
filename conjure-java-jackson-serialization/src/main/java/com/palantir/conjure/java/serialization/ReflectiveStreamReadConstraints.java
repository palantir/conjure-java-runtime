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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.TSFBuilder;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.lang.reflect.Method;

/**
 * This class exists to ensure default values match our expectations in cases where jackson is
 * upgraded transitively prior to a CJR release which expects 2.15.0+. After this library upgrades, the
 * reflection may be replaced by the following:
 *<pre>{@code
 * return builder.streamReadConstraints(StreamReadConstraints.builder()
 *         .maxStringLength(MAX_STRING_LENGTH)
 *         .build());
 * }</pre>
 */
final class ReflectiveStreamReadConstraints {
    private static final SafeLogger log = SafeLoggerFactory.get(ReflectiveStreamReadConstraints.class);

    // 50mb up from the default 5mb as a more permissive value to begin with, which we can ratchet down over time.
    // This allows us to decouple the initial risk of adopting string length limits from the risk introduced by taking
    // a dependency upgrade.
    private static final int MAX_STRING_LENGTH = 50_000_000;

    @SuppressWarnings("unchecked")
    static <F extends JsonFactory, B extends TSFBuilder<F, B>> B withDefaultConstraints(B builder) {
        try {
            // Use the same classloader which loaded the TSFBuilder
            ClassLoader classLoader = builder.getClass().getClassLoader();
            Class<?> streamReadConstraintsClass =
                    Class.forName("com.fasterxml.jackson.core.StreamReadConstraints", true, classLoader);
            Object constraints = createConjureDefaultStreamReadConstraints(streamReadConstraintsClass);
            return (B) builder.getClass()
                    .getMethod("streamReadConstraints", streamReadConstraintsClass)
                    .invoke(builder, constraints);
        } catch (ClassNotFoundException cnfe) {
            // Log at debug in the expected case using jackson 2.14 which does not support limits.
            log.debug("StreamReadConstraints class does not exist, nothing to do", cnfe);
        } catch (ReflectiveOperationException e) {
            log.warn("Failed to update StreamReadConstraints, upstream default values will be used", e);
        }
        return builder;
    }

    private static Object createConjureDefaultStreamReadConstraints(Class<?> streamReadConstraintsClass)
            throws ReflectiveOperationException {
        Method builderMethod = streamReadConstraintsClass.getMethod("builder");
        Object streamReadConstraintsBuilder = builderMethod.invoke(null);
        Class<?> streamReadConstraintsBuilderClass = streamReadConstraintsBuilder.getClass();
        // Default to a max size of 50mb (up from the 5mb default)
        streamReadConstraintsBuilderClass
                .getMethod("maxStringLength", int.class)
                .invoke(streamReadConstraintsBuilder, MAX_STRING_LENGTH);
        return streamReadConstraintsBuilderClass.getMethod("build").invoke(streamReadConstraintsBuilder);
    }

    private ReflectiveStreamReadConstraints() {}
}
