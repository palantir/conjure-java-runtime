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

import com.google.common.collect.Sets;
import com.palantir.logsafe.SafeArg;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestContext {
    private static final Logger log = LoggerFactory.getLogger(RequestContext.class);

    private RequestContext() {}

    private static final ThreadLocal<MappedContext> currentContext = ThreadLocal.withInitial(MappedContext::new);

    static MappedContext currentContext() {
        return currentContext.get();
    }

    /**
     * Gets a Contextual from the RequestContext.  Returns empty if the key is not present or has a null-value.
     */
    public static Optional<Contextual<?>> get(String key) {
        return Optional.ofNullable(currentContext().get(key));
    }

    /**
     * Gets a specific type of Contextual from the RequestContext.  If the key is not present, has a null-value, or the
     * value type is not that of the given class, returns an empty optional instead.
     */
    public static <T extends Contextual<T>> Optional<T> getInstance(String key, Class<T> clazz) {
        Contextual<?> contextual = currentContext().get(key);
        if (contextual == null) {
            return Optional.empty();
        }
        if (!clazz.isAssignableFrom(contextual.getClass())) {
            log.warn("Invalid class found under RequestContext key.", SafeArg.of("key", key),
                    SafeArg.of("expected", clazz.getCanonicalName()),
                    SafeArg.of("actual", contextual.getClass().getCanonicalName()));
            return Optional.empty();
        }
        return Optional.of(clazz.cast(contextual));
    }

    /**
     * Returns a stored boolean value, or the default if the key isn't present, has a null-value, or a value type which
     * represents a different class.
     */
    public static boolean getBoolean(String key, boolean defaultResponse) {
        return getPrimitive(key, defaultResponse);
    }

    /**
     * Returns a stored String value, or the default if the key isn't present, has a null-value, or a value type which
     * represents a different class.
     */
    public static String getString(String key, String defaultResponse) {
        return getPrimitive(key, defaultResponse);
    }

    /**
     * Returns a stored integer value, or the default if the key isn't present, has a null-value, or a value type which
     * represents a different class.
     */
    public static int getInteger(String key, int defaultResponse) {
        return getPrimitive(key, defaultResponse);
    }

    /**
     * Returns a stored long value, or the default if the key isn't present, has a null-value, or a value type which
     * represents a different class.
     */
    public static long getLong(String key, long defaultResponse) {
        return getPrimitive(key, defaultResponse);
    }

    /**
     * Returns a stored double value, or the default if the key isn't present, has a null-value, or a value type which
     * represents a different class.
     */
    public static double getDouble(String key, double defaultResponse) {
        return getPrimitive(key, defaultResponse);
    }

    /** Sets the RequestContext key to a specific Contextual. */
    public static void set(String key, Contextual<?> contextual) {
        Contextual old = currentContext().put(key, contextual);
        try {
            if (old != null) {
                old.onUnset();
            }
        } finally {
            contextual.onSet();
        }
    }

    /** Sets a boolean value for a key.  The value is copied directly to other threads. */
    public static void setBoolean(String key, boolean value) {
        setPrimitive(key, value);
    }

    /** Sets a String value for a key.  The value is copied directly to other threads. */
    public static void setString(String key, String value) {
        setPrimitive(key, value);
    }

    /** Sets a Integer value for a key.  The value is copied directly to other threads. */
    public static void setInteger(String key, int value) {
        setPrimitive(key, value);
    }

    /** Sets a long value for a key.  The value is copied directly to other threads. */
    public static void setLong(String key, long value) {
        setPrimitive(key, value);
    }

    /** Sets a double value for a key.  The value is copied directly to other threads. */
    public static void setDouble(String key, double value) {
        setPrimitive(key, value);
    }

    public static void remove(String key) {
        Contextual<?> contextual = currentContext().remove(key);
        if (contextual != null) {
            contextual.onUnset();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPrimitive(String key, T defaultValue) {
        Optional<PrimitiveContextual> contextual = getInstance(key, PrimitiveContextual.class);
        return contextual.map(PrimitiveContextual::value)
                .filter(value -> {
                    if (!defaultValue.getClass().isAssignableFrom(value.getClass())) {
                        log.warn("Expected RequestContext primitive under key is of different type.",
                                SafeArg.of("key", key),
                                SafeArg.of("expected", defaultValue.getClass().getCanonicalName()),
                                SafeArg.of("actual", value.getClass().getCanonicalName()));
                        return false;
                    }
                    return defaultValue.getClass().isAssignableFrom(value.getClass());
                })
                .map(value -> (T) value)
                .orElse(defaultValue);
    }

    private static <T> void setPrimitive(String key, T value) {
        set(key, new PrimitiveContextual<>(value));
    }

    /**
     * Clears the current context and returns (a copy of) it; used internally for setting the correct context on task
     * executions.
     */
    static MappedContext getAndClear() {
        MappedContext context = currentContext();
        context.values().forEach(Contextual::onUnset);
        currentContext.remove();
        return context;
    }

    /** Directly sets the current context; used internally for setting the correct context on task executions. */
    static void setContext(MappedContext context) {
        getAndClear();
        currentContext.set(context);
        currentContext().values().forEach(Contextual::onSet);
    }

    static class MappedContext extends HashMap<String, Contextual<?>> {
        MappedContext() {}

        MappedContext(Map<? extends String, ? extends Contextual<?>> mappings) {
            super(mappings);
        }

        MappedContext deepCopy(String... excluding) {
            MappedContext copy = new MappedContext();
            Set<String> excludes = Sets.newHashSet(excluding);
            forEach((key, value) -> {
                if (!excludes.contains(key)) {
                    copy.put(key, value.taskCopy());
                }
            });
            return copy;
        }

        MappedContext shallowCopy() {
            return new MappedContext(this);
        }
    }
}
