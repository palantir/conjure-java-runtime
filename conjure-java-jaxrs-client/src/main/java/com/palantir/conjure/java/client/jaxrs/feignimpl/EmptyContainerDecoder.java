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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import feign.Response;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interprets HTTP 204 as an 'empty' type using Jackson initially, then using reflection to manually invoke a static
 * factory annotated with {@link JsonCreator}.
 *
 * <p>Empty instances are cached and re-used to avoid reflection and exceptions on a hot codepath.
 */
public final class EmptyContainerDecoder implements Decoder {

    private final LoadingCache<Type, Object> blankInstanceCache;
    private final Decoder delegate;

    public EmptyContainerDecoder(ObjectMapper mapper, Decoder delegate) {
        this.delegate = delegate;
        this.blankInstanceCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(new BlankInstanceLoader(mapper));
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        Object delegateResult = delegate.decode(response, type);

        if (response.status() == 204 || (response.status() == 200 && delegateResult == null)) {
            @Nullable Object object = blankInstanceCache.get(type);
            return Preconditions.checkNotNull(
                    object, "Received HTTP 204 but unable to construct an empty instance for return type", SafeArg.of(
                            "type", type));
        } else {
            return delegateResult;
        }
    }

    private static class BlankInstanceLoader implements CacheLoader<Type, Object> {
        private static final Logger log = LoggerFactory.getLogger(BlankInstanceLoader.class);
        private final ObjectMapper mapper;

        BlankInstanceLoader(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Nullable
        @Override
        public Object load(@Nonnull Type type) {
            return constructEmptyInstance(RawTypes.get(type), type, 10).orElse(null);
        }

        private Optional<Object> constructEmptyInstance(Class<?> clazz, Type originalType, int maxRecursion) {
            // handle Map, List, Set
            Optional<Object> collection = coerceCollections(clazz);
            if (collection.isPresent()) {
                return collection;
            }

            // this is our preferred way to construct instances
            Optional<Object> jacksonInstance = jacksonDeserializeFromNull(clazz);
            if (jacksonInstance.isPresent()) {
                return jacksonInstance;
            }

            // fallback to manual reflection to handle aliases of optionals (and aliases of aliases of optionals)
            Optional<Method> jsonCreator = getJsonCreatorStaticMethod(clazz);
            if (jsonCreator.isPresent()) {
                Method method = jsonCreator.get();
                Class<?> parameterType = method.getParameters()[0].getType();
                Optional<Object> parameter =
                        constructEmptyInstance(parameterType, originalType, decrement(maxRecursion, originalType));

                if (parameter.isPresent()) {
                    return invokeStaticFactoryMethod(method, parameter.get());
                } else {
                    log.debug(
                            "Found a @JsonCreator, but couldn't construct the parameter",
                            SafeArg.of("type", originalType),
                            SafeArg.of("parameter", parameter));
                    return Optional.empty();
                }
            }

            log.debug(
                    "Jackson couldn't instantiate an empty instance and also couldn't find a usable @JsonCreator",
                    SafeArg.of("type", originalType));
            return Optional.empty();
        }

        private static int decrement(int maxRecursion, Type originalType) {
            Preconditions.checkState(
                    maxRecursion > 0,
                    "Unable to construct an empty instance as @JsonCreator requires too much recursion",
                    SafeArg.of("type", originalType));
            return maxRecursion - 1;
        }

        private static Optional<Object> coerceCollections(Class<?> clazz) {
            if (List.class.isAssignableFrom(clazz)) {
                return Optional.of(Collections.emptyList());
            } else if (Set.class.isAssignableFrom(clazz)) {
                return Optional.of(Collections.emptySet());
            } else if (Map.class.isAssignableFrom(clazz)) {
                return Optional.of(Collections.emptyMap());
            } else {
                return Optional.empty();
            }
        }

        private Optional<Object> jacksonDeserializeFromNull(Class<?> clazz) {
            try {
                return Optional.ofNullable(mapper.readValue("null", clazz));
            } catch (IOException e) {
                return Optional.empty();
            }
        }

        // doesn't attempt to handle multiple @JsonCreator methods on one class
        private static Optional<Method> getJsonCreatorStaticMethod(@Nonnull Class<?> clazz) {
            return Arrays.stream(clazz.getMethods())
                    .filter(method -> Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 1
                            && method.getAnnotation(JsonCreator.class) != null)
                    .findFirst();
        }

        private static Optional<Object> invokeStaticFactoryMethod(Method method, Object parameter) {
            try {
                return Optional.ofNullable(method.invoke(null, parameter));
            } catch (IllegalAccessException | InvocationTargetException e) {
                log.debug("Reflection instantiation failed", e);
                return Optional.empty();
            }
        }
    }
}
