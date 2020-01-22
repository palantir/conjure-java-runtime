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

package com.palantir.conjure.java.ext.refresh;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/** A layman's Observable: Stores a reference to a value until it is {@link #getAndClear retrieved} once. */
@ThreadSafe
public final class Refreshable<T> {

    private final AtomicReference<T> value;

    private Refreshable(@Nullable T initialValue) {
        value = new AtomicReference<>(initialValue);
    }

    public static <T> Refreshable<T> empty() {
        return new Refreshable<>(null);
    }

    public static <T> Refreshable<T> of(T value) {
        return new Refreshable<>(value);
    }

    /** Sets the stored value to the given value and returns the previously stored value if it exists. */
    public Optional<T> set(T newValue) {
        return Optional.ofNullable(value.getAndSet(newValue));
    }

    /**
     * Returns the currently stored value if it exists and clears it. For instance, the following sequence of events,
     * {@code set(a), getAndClear, getAndClear, set(b), getAndClear, getAndClear} yield values {@code a, empty, b,
     * empty} for the four {@link #getAndClear} calls.
     */
    public Optional<T> getAndClear() {
        return Optional.ofNullable(value.getAndSet(null));
    }
}
