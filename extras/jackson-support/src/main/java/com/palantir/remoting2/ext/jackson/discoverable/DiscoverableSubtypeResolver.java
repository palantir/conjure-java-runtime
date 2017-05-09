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

import com.fasterxml.jackson.databind.jsontype.impl.StdSubtypeResolver;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * A custom resolver that will register with an ObjectMapper all the classes in the hierarchy starting from the provided
 * root class ({@link Discoverable} by default).
 * <p>
 * NOTE: The way subclasses are discovered depends on the provided {@link SubtypeDiscoverer}. The default one will only
 *       load subclasses that can be found within files in the {@code META-INF/services} folder in a jar.
 */
@SuppressWarnings("WeakerAccess") // Allows users of this library to customize the behavior
public final class DiscoverableSubtypeResolver extends StdSubtypeResolver {
    private final Set<Class<?>> discoveredTypes;

    public DiscoverableSubtypeResolver() {
        this(Discoverable.class);
    }

    public DiscoverableSubtypeResolver(Class<?> rootClass) {
        this(new MetaInfBasedDiscoverer(), rootClass);
    }

    public DiscoverableSubtypeResolver(SubtypeDiscoverer discoverer, Class<?> rootClass) {
        discoveredTypes = Collections.unmodifiableSet(discoverSubTypes(discoverer, rootClass));
    }

    private Set<Class<?>> discoverSubTypes(SubtypeDiscoverer discoverer, Class<?> rootClass) {
        Set<Class<?>> discovered = new HashSet<>();
        Queue<Class<?>> toBeProcessed = new LinkedList<>();

        toBeProcessed.offer(rootClass);

        while (!toBeProcessed.isEmpty()) {
            Class<?> clazz = toBeProcessed.poll();

            discoverer.discoverSubtypes(clazz).stream()
                    .filter(subtype -> !discovered.contains(subtype))
                    .forEach(subtype -> {
                        discovered.add(subtype);
                        toBeProcessed.offer(subtype);
                    });
        }

        return discovered;
    }
}
