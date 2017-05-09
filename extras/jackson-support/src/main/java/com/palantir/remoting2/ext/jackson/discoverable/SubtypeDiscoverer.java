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

package com.palantir.remoting2.ext.jackson.discoverable;

import java.util.List;

/**
 * This component is responsible for discovering the Jackson-serializable direct subclasses of a given class/interface.
 * By immediate subclasses we mean subclasses that explicitly extend/implement the given class/interface, for example,
 * if A extends B, B extends C, B is a direct subclass of A, while C is not.
 */
@FunctionalInterface
public interface SubtypeDiscoverer {

    /**
     * Finds all the Jackson-serializable direct subclasses of a given class.
     *
     * @param superClass The class for which to find the direct subclasses
     */
    List<Class<?>> discoverSubtypes(Class<?> superClass);
}
