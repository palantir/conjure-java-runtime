/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.type.TypeModifier;
import com.fasterxml.jackson.databind.type.TypeParser;
import com.fasterxml.jackson.databind.util.ArrayBuilders;
import com.fasterxml.jackson.databind.util.LRUMap;
import com.fasterxml.jackson.databind.util.LookupCache;
import javax.annotation.Nullable;

/**
 * A {@link TypeFactory} implementation which does not use a cache.
 * @see <a href="https://github.com/FasterXML/jackson-databind/issues/3876">jackson-databind#3876</a>
 * @see <a href="https://github.com/FasterXML/jackson-benchmarks/pull/5">jackson-benchmarks#5</a>
 */
final class NonCachingTypeFactory extends TypeFactory {

    /**
     * Attempt to produce a {@link NonCachingTypeFactory} based on the given {@link TypeFactory}.
     * If the provided TypeFactory is non-default, this may not be possible to do safely
     * and the original will be returned.
     */
    static TypeFactory from(TypeFactory original) {
        if (original instanceof NonCachingTypeFactory) {
            return original;
        }
        if (original == TypeFactory.defaultInstance()) {
            return new NonCachingTypeFactory();
        }
        // As a fallback we update the existing factory with a non-caching cache implementation. Unfortunately this
        // is not likely to survive any module registration which adds a TypeModifier, but it's the best we can do.
        // If we return a new instance which doesn't preserve existing TypeModifiers, deserialization may fail.
        return original.withCache(NoCacheLookupCache.INSTANCE);
    }

    private NonCachingTypeFactory() {
        super(NoCacheLookupCache.INSTANCE);
    }

    private NonCachingTypeFactory(TypeParser parser, @Nullable TypeModifier[] modifiers, ClassLoader classLoader) {
        super(NoCacheLookupCache.INSTANCE, parser, modifiers, classLoader);
    }

    @Override
    public NonCachingTypeFactory withModifier(TypeModifier mod) {
        return new NonCachingTypeFactory(_parser, computeModifiers(_modifiers, mod), _classLoader);
    }

    @Nullable
    private static TypeModifier[] computeModifiers(TypeModifier[] existing, TypeModifier newModifier) {
        // Semantics are based on the jackson-databind `withModifier` implementation.
        if (newModifier == null) {
            return null;
        }
        if (existing == null || existing.length == 0) {
            return new TypeModifier[] {newModifier};
        }
        return ArrayBuilders.insertInListNoDup(existing, newModifier);
    }

    @Override
    public NonCachingTypeFactory withClassLoader(ClassLoader classLoader) {
        return new NonCachingTypeFactory(_parser, _modifiers, classLoader);
    }

    @Override
    public NonCachingTypeFactory withCache(LRUMap<Object, JavaType> _cache) {
        // Changes to the cache are ignored
        return this;
    }

    @Override
    public NonCachingTypeFactory withCache(LookupCache<Object, JavaType> _cache) {
        // Changes to the cache are ignored
        return this;
    }

    private enum NoCacheLookupCache implements LookupCache<Object, JavaType> {
        INSTANCE;

        @Override
        public int size() {
            return 0;
        }

        @Override
        @Nullable
        public JavaType get(Object _key) {
            return null;
        }

        @Override
        @Nullable
        public JavaType put(Object _key, JavaType _value) {
            return null;
        }

        @Override
        public JavaType putIfAbsent(Object _key, JavaType _value) {
            return null;
        }

        @Override
        public void clear() {}

        @Override
        public String toString() {
            return "NoCacheLookupCache{}";
        }
    }
}
