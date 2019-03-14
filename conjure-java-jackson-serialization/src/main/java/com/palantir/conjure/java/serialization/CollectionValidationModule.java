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

package com.palantir.conjure.java.serialization;

import com.fasterxml.jackson.databind.module.SimpleModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates that collections are deserialized into data that Conjure allows. This module provides <i>soft</i>
 * validation, logging instead of breaking functionality. Over time this will transition to strict validation,
 * throwing exceptions when incorrect data is received.
 * This implementation allows us to discover code which leverages incorrect behavior and remediate without
 * breaking functionality.
 *
 * What is validated?
 * <ul>
 *     <li>Collection values must not be null</li>
 *     <li>Maps must not contain duplicate keys</li>
 * </ul>
 * What is not validated?
 * <ul>
 *     <li>This implementation does not validate that deserialized collections are not modified.</li>
 * </ul>
 */
final class CollectionValidationModule extends SimpleModule {

    private static final Logger log = LoggerFactory.getLogger(CollectionValidationModule.class);

    CollectionValidationModule() {
        super("collection-validation");
        addAbstractTypeMapping(List.class, ValidatingArrayList.class);
        addAbstractTypeMapping(Set.class, ValidatingHashSet.class);
        addAbstractTypeMapping(Map.class, ValidatingHashMap.class);
    }

    /** Logs noisily if null values are received. */
    static final class ValidatingArrayList<T> extends ArrayList<T> {

        @Override
        public boolean add(T value) {
            return super.add(logIfNull(value));
        }

        @Override
        public void add(int index, T value) {
            super.add(index, logIfNull(value));
        }

        @Override
        public boolean addAll(Collection<? extends T> collection) {
            collection.forEach(CollectionValidationModule::logIfNull);
            return super.addAll(collection);
        }

        @Override
        public boolean addAll(int index, Collection<? extends T> collection) {
            collection.forEach(CollectionValidationModule::logIfNull);
            return super.addAll(index, collection);
        }

        @Override
        public T set(int index, T value) {
            return super.set(index, logIfNull(value));
        }
    }

    /** Logs noisily if null values are received. */
    static final class ValidatingHashSet<T> extends HashSet<T> {

        @Override
        public boolean add(T value) {
            return super.add(logIfNull(value));
        }

        @Override
        public boolean addAll(Collection<? extends T> collection) {
            collection.forEach(CollectionValidationModule::logIfNull);
            return super.addAll(collection);
        }
    }

    /** Logs noisily if null values are received. */
    static final class ValidatingHashMap<K, V> extends HashMap<K, V> {
        private static final BiConsumer<Object, Object> validator = (key, value) -> logIfNull(value);

        @Override
        public V put(K key, V value) {
            V previousValue = super.put(key, logIfNull(value));
            if (previousValue != null) {
                // Avoid the performance cost of Throwable.fillInStackTrace if WARN has been disabled
                if (log.isWarnEnabled()) {
                    // TODO(ckozak): Log key and values as unsafe arguments. This would require us to take a
                    // dependency on safe-logging.
                    log.warn("Detected duplicate map keys which are not allowed by Conjure",
                            new IllegalArgumentException());
                }
                assert false : "Duplicate values for the same key are not allowed by Conjure. Key '"
                        + key + "' values ['" + value + "', '" + previousValue + "']";
            }
            return previousValue;
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> map) {
            map.forEach(validator);
            super.putAll(map);
        }
    }

    @Nullable
    private static <T> T logIfNull(@Nullable T value) {
        // Log noisily in production
        if (value == null
                // Avoid the performance cost of Throwable.fillInStackTrace if WARN has been disabled
                && log.isWarnEnabled()) {
            log.warn("Detected a null value, null values are not allowed by Conjure", new IllegalArgumentException());
        }
        // Tests should fail, but we're not ready to cause breaks in deployed systems yet.
        assert value != null : "Null values are not allowed by Conjure";
        return value;
    }
}
