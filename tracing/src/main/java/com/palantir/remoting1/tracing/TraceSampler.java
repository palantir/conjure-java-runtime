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

package com.palantir.remoting1.tracing;

/**
 * Nullary predicate that returns true iff the trace under consideration should be presented to the configured {@link
 * SpanObserver observers}. Implementations must be thread-safe. The sampler is invoked synchronously on the thread
 * calling into {@link Tracer} and must only do non-trivial work (e.g., at most drawing a random number).
 */
public interface TraceSampler {
    boolean sample();
}
