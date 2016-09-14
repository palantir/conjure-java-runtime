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

package com.palantir.remoting1.servers;

import com.google.common.base.Preconditions;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for tracing components and operations.
 */
@SuppressWarnings("WeakerAccess")
public final class Tracers {
    private static final Logger logger = LoggerFactory.getLogger(Tracers.class);

    private static final AtomicReference<Tracer> activeTracer = new AtomicReference<>(noOpTracer());

    private Tracers() {}

    public static Tracer activeTracer() {
        return activeTracer.get();
    }

    public static void setActiveTracer(Tracer newTracer) {
        Preconditions.checkNotNull(newTracer, "Tracer cannot be null");
        boolean updated = activeTracer.compareAndSet(NoOpTracer.INSTANCE, newTracer);
        if (updated) {
            logger.info("Switched from NoOpTracer to {}", newTracer);
            return;
        }

        Tracer previousTracer = activeTracer.getAndSet(newTracer);
        if (previousTracer != NoOpTracer.INSTANCE && !previousTracer.equals(newTracer)) {
            // Rarely do you actually want to switch the active tracer from a non-no-op one to another,
            // but if so warn the user
            logger.warn("Attempted to replace activeTracer {} with {}", previousTracer, newTracer);
        }
        logger.info("Tracing enabled with activeTracer {}", activeTracer());
    }

    public static void trace(String component, String operation, Runnable runnable) {
        //noinspection unused - try-with-resources autocloseable
        try (Tracer.TraceContext trace = activeTracer().begin(component, operation)) {
            runnable.run();
        }
    }

    public static <T> T trace(String component, String operation, Callable<T> callable) throws Exception {
        //noinspection unused - try-with-resources autocloseable
        try (Tracer.TraceContext trace = activeTracer().begin(component, operation)) {
            return callable.call();
        }
    }

    public static Tracer noOpTracer() {
        return NoOpTracer.INSTANCE;
    }

    private enum NoOpTracer implements Tracer {
        INSTANCE {
            @Override
            public TraceContext begin(String component, String operation) {
                return NoOpContext.INSTANCE;
            }

            @Override
            public Class<? extends TraceContext> getContextClass() {
                return TraceContext.class;
            }
        }
    }

    private enum NoOpContext implements Tracer.TraceContext {
        INSTANCE {
            @Override
            public void close() {
                // no-op
            }
        }
    }

}
