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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.primitives.Ints;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import javax.annotation.Nullable;

final class CaffeineCachingTypeFactory extends TypeFactory {
    private static final SafeLogger log = SafeLoggerFactory.get(CaffeineCachingTypeFactory.class);

    CaffeineCachingTypeFactory() {
        super(new CaffeineLookupCache());
    }

    CaffeineCachingTypeFactory(
            CaffeineLookupCache typeCache,
            TypeParser parser,
            @Nullable TypeModifier[] modifiers,
            ClassLoader classLoader) {
        super(typeCache, parser, modifiers, classLoader);
    }

    private static CaffeineLookupCache asCaffeine(LookupCache<Object, JavaType> typeCache) {
        if (typeCache instanceof CaffeineLookupCache) {
            return (CaffeineLookupCache) typeCache;
        } else if (typeCache == null || typeCache.size() == 0) {
            // Use a caffeine cache instead of the provided empty cache
            return new CaffeineLookupCache();
        } else if (typeCache instanceof LRUMap) {
            CaffeineLookupCache cache = new CaffeineLookupCache();
            LRUMap<Object, JavaType> defaultJacksonCacheType = (LRUMap<Object, JavaType>) typeCache;
            defaultJacksonCacheType.contents(cache::put);
            return cache;
        } else {
            log.error(
                    "Unknown LookupCache, using a new caffeine cache instead",
                    SafeArg.of("cacheType", typeCache.getClass()));
            return new CaffeineLookupCache();
        }
    }

    @Override
    public CaffeineCachingTypeFactory withModifier(TypeModifier mod) {
        // Updating modifiers clears the cache
        return new CaffeineCachingTypeFactory(
                new CaffeineLookupCache(), _parser, computeModifiers(_modifiers, mod), _classLoader);
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
    public CaffeineCachingTypeFactory withClassLoader(ClassLoader classLoader) {
        return new CaffeineCachingTypeFactory(asCaffeine(_typeCache), _parser, _modifiers, classLoader);
    }

    @Override
    public CaffeineCachingTypeFactory withCache(LRUMap<Object, JavaType> cache) {
        return withCache((LookupCache<Object, JavaType>) cache);
    }

    @Override
    public CaffeineCachingTypeFactory withCache(LookupCache<Object, JavaType> cache) {
        return new CaffeineCachingTypeFactory(asCaffeine(cache), _parser, _modifiers, _classLoader);
    }

    private static final class CaffeineLookupCache implements LookupCache<Object, JavaType> {
        private final Cache<Object, JavaType> cache;

        CaffeineLookupCache() {
            this.cache = Caffeine.newBuilder()
                    // max-size 1000 up from 200 default as of 2.14.2
                    .maximumSize(1000)
                    // initial-size 128 up from 16 default as of 2.14.2
                    .initialCapacity(128)
                    .build();
        }

        @Override
        public int size() {
            return Ints.saturatedCast(cache.estimatedSize());
        }

        @Override
        @Nullable
        public JavaType get(Object key) {
            return cache.getIfPresent(key);
        }

        @Override
        public JavaType put(Object key, JavaType value) {
            return cache.asMap().put(key, value);
        }

        @Override
        public JavaType putIfAbsent(Object key, JavaType value) {
            return cache.asMap().putIfAbsent(key, value);
        }

        @Override
        public void clear() {
            cache.invalidateAll();
        }

        @Override
        public String toString() {
            return "CaffeineLookupCache{" + cache + '}';
        }
    }
}
