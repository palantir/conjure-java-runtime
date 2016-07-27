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

package com.palantir.remoting1.ext.brave;

import com.github.kristofa.brave.LoggingSpanCollector;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.internal.Util;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link SpanCollector} implementation that logs through SLF4J at INFO level.
 */
public final class SlfLoggingSpanCollector implements SpanCollector {

    private final Logger log;
    private final Set<BinaryAnnotation> annotations = new LinkedHashSet<>();

    public SlfLoggingSpanCollector() {
        log = LoggerFactory.getLogger(LoggingSpanCollector.class.getName());
    }

    public SlfLoggingSpanCollector(String loggerName) {
        Util.checkNotBlank(loggerName, "loggerName must not be blank or null");
        log = LoggerFactory.getLogger(loggerName);
    }

    @Override
    public void collect(Span span) {
        Util.checkNotNull(span, "span must not be null");
        for (BinaryAnnotation ba : annotations) {
            span.addToBinary_annotations(ba);
        }

        log.info("{}", span);
    }

    @Override
    public void addDefaultAnnotation(String key, String value) {
        annotations.add(BinaryAnnotation.create(key, value, null));
    }
}
