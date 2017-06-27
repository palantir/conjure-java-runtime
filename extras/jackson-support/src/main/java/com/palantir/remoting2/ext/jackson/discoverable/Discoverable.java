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

import com.fasterxml.jackson.annotation.JsonSubTypes;

/**
 * Entry-point for Jackson subtype discovery. Top-level interfaces that need to be available to the object mapper.
 * A custom {@link com.fasterxml.jackson.databind.jsontype.SubtypeResolver SubtypeResolver} will look for entries in
 * {@code META-INF/services/com.palantir.remoting2.ext.jackson.discoverable.Discoverable} and recursively add all the
 * classes in the hierarchy, provided that they are listed within files in {@code META-INF/services} according to the
 * following rules:
 *
 * <ol>
 *     <li> All top-level classes are expected to be found in a file named
 *          {@code com.palantir.remoting2.ext.jackson.discoverable.Discoverable} within the META-INF/services folder in
 *          the jar.
 *     </li>
 *     <li> All other classes can be put either in the file mentioned above, or in a file named like the fully-qualified
 *          name of their parent (e.g. com.palantir.examples.Value) within the META-INF/services folder in the jar
 *     </li>
 * </ol>
 *
 * NOTE: Subclasses that do not appear in any of the files mentioned above will be ignored!
 *
 * This is especially powerful if used in combination with Google's {@code @AutoService} annotation, which will generate
 * the META-INF/services files at compile time.
 *
 * {@code Discoverable} and {@code @AutoService} allow a developer not to have to provide
 * {@link JsonSubTypes @JsonSubTypes} enumerating all the implementations of an interface.
 *
 * <p>
 * Example:
 * Assuming package {@code com.palantir.examples}
 * <p>
 * {@code @AutoService(Discoverable.class)}
 * <br>
 * {@code interface Value implements Discoverable { }}
 * <p>
 * {@code @AutoService(Value.class)}
 * <br>
 * {@code interface ConcreteValue implements Value { }}
 * <p>
 * Example content of {@code META-INF/services}:
 * <ol>
 *     <li>file with name "{@code com.palantir.remoting2.ext.jackson.discoverable.Discoverable}", content:</li>
 *          <ul>
 *              <li>{@code com.palantir.examples.Value}</li>
 *          </ul>
 *
 *     <li>file with name "{@code com.palantir.examples.Value}", content:</li>
 *          <ul>
 *              <li>{@code com.palantir.examples.ConcreteValue}</li>
 *          </ul>
 * </ol>
 */
public interface Discoverable { }
