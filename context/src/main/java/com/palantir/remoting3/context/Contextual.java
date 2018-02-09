/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.remoting3.context;

/**
 * Represents an item which can be stored in the {@link RequestContext} and copied across to other threads, persisting
 * the context state during task execution.
 */
public interface Contextual<T extends Contextual<T>> {
    /** Called after Contextual is set in the RequestContext. */
    default void onSet() {}
    /** Called after Contextual is unset in the RequestContext. */
    default void onUnset() {}
    /** Create a copy of the current Contextual to use when running tasks. */
    T taskCopy();
}
