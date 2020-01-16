/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LeakDetector<T> {
    private static final Logger log = LoggerFactory.getLogger(LeakDetector.class);

    private final Class<T> resourceType;
    private final Consumer<Optional<RuntimeException>> subscriber;
    private final List<LeakDetectingReference<T>> references = new ArrayList<>();

    LeakDetector(Class<T> resourceType) {
        this(resourceType, unused -> {});
    }

    @VisibleForTesting
    LeakDetector(Class<T> resourceType, Consumer<Optional<RuntimeException>> subscriber) {
        this.resourceType = resourceType;
        this.subscriber = subscriber;
    }

    static Optional<RuntimeException> maybeCreateStackTrace() {
        if (log.isTraceEnabled()) {
            return Optional.of(new SafeRuntimeException("Runtime exception for stack trace"));
        }
        return Optional.empty();
    }

    synchronized void register(T objectToMonitor, Optional<RuntimeException> stackTrace) {
        references.add(new LeakDetectingReference<>(objectToMonitor, stackTrace));
        pruneAndLog();
    }

    synchronized void unregister(T objectToNoLongerMonitor) {
        for (int i = 0; i < references.size(); i++) {
            if (references.get(i).get() == objectToNoLongerMonitor) {
                int last = references.size() - 1;
                Collections.swap(references, i, last);
                references.remove(last);
                return;
            }
        }
    }

    private synchronized void pruneAndLog() {
        Iterator<LeakDetectingReference<T>> iterator = references.iterator();
        while (iterator.hasNext()) {
            LeakDetectingReference<T> reference = iterator.next();
            if (reference.get() == null) {
                subscriber.accept(reference.stackTrace);
                logLeak(reference.stackTrace);
                iterator.remove();
            }
        }
    }

    private void logLeak(Optional<RuntimeException> stackTrace) {
        if (stackTrace.isPresent()) {
            log.warn(
                    "Resource leak detected - did you forget to close a resource properly? "
                            + "This will likely hurt performance. Stack trace is of the call where "
                            + "the acquire happened.",
                    SafeArg.of("resourceType", resourceType.getName()),
                    stackTrace.get());
        } else {
            log.warn(
                    "Leak detected in Conjure call - did you forget to close a resource properly? "
                            + "This will likely hurt performance. To get a "
                            + "stack trace for the call where the acquire happened, set log "
                            + "level to TRACE.",
                    SafeArg.of("resourceType", resourceType.getName()),
                    SafeArg.of("loggerToSetToTrace", log.getName()));
        }
    }

    private static final class LeakDetectingReference<T> extends WeakReference<T> {
        private final Optional<RuntimeException> stackTrace;

        LeakDetectingReference(T referent, Optional<RuntimeException> stackTrace) {
            super(referent);
            this.stackTrace = stackTrace;
        }
    }
}
