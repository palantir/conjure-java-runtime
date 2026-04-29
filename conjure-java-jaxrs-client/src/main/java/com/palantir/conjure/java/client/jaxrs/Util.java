/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.conjure.java.client.jaxrs;

import com.google.errorprone.annotations.FormatMethod;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Utilities, typically copied in from guava, so as to avoid dependency conflicts.
 */
final class Util {

    /**
     * The HTTP Content-Length header field name.
     */
    static final String CONTENT_LENGTH = "Content-Length";

    private static final int BUF_SIZE = 0x800; // 2K chars (4K bytes)

    /**
     * Type literal for {@code Map<String, ?>}.
     */
    static final Type MAP_STRING_WILDCARD = new Types.ParameterizedTypeImpl(
            null, Map.class, String.class, new Types.WildcardTypeImpl(new Type[] {Object.class}, new Type[0]));

    private Util() { // no instances
    }

    /**
     * Copy of {@code com.google.common.base.Preconditions#checkArgument}.
     */
    @FormatMethod
    static void checkArgument(boolean expression, String errorMessageTemplate, Object... errorMessageArgs) {
        if (!expression) {
            throw new IllegalArgumentException(String.format(errorMessageTemplate, errorMessageArgs));
        }
    }

    /**
     * Copy of {@code com.google.common.base.Preconditions#checkNotNull}.
     */
    @FormatMethod
    static <T> T checkNotNull(T reference, String errorMessageTemplate, Object... errorMessageArgs) {
        if (reference == null) {
            // If either of these parameters is null, the right thing happens anyway
            throw new NullPointerException(String.format(errorMessageTemplate, errorMessageArgs));
        }
        return reference;
    }

    /**
     * Copy of {@code com.google.common.base.Preconditions#checkState}.
     */
    @FormatMethod
    static void checkState(boolean expression, String errorMessageTemplate, Object... errorMessageArgs) {
        if (!expression) {
            throw new IllegalStateException(String.format(errorMessageTemplate, errorMessageArgs));
        }
    }

    /**
     * Adapted from {@code com.google.common.base.Strings#emptyToNull}.
     */
    static String emptyToNull(String string) {
        return string == null || string.isEmpty() ? null : string;
    }

    /**
     * Adapted from {@code com.google.common.base.Strings#emptyToNull}.
     */
    static <T> T[] toArray(Iterable<? extends T> iterable, Class<T> type) {
        Collection<T> collection;
        if (iterable instanceof Collection) {
            collection = (Collection<T>) iterable;
        } else {
            collection = new ArrayList<T>();
            for (T element : iterable) {
                collection.add(element);
            }
        }
        T[] array = (T[]) Array.newInstance(type, collection.size());
        return collection.toArray(array);
    }

    /**
     * Returns an unmodifiable collection which may be empty, but is never null.
     */
    static <T> Collection<T> valuesOrEmpty(Map<String, Collection<T>> map, String key) {
        return map.containsKey(key) && map.get(key) != null ? map.get(key) : Collections.<T>emptyList();
    }

    static void ensureClosed(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) { // NOPMD
            }
        }
    }

    /**
     * This returns well known empty values for well-known java types. This returns null for types not
     * in the following list.
     *
     * <ul>
     *   <li>{@code [Bb]oolean}</li>
     *   <li>{@code byte[]}</li>
     *   <li>{@code Collection}</li>
     *   <li>{@code Iterator}</li>
     *   <li>{@code List}</li>
     *   <li>{@code Map}</li>
     *   <li>{@code Set}</li>
     * </ul>
     *
     * <p/> When {@link Feign.Builder#decode404() decoding HTTP 404 status}, you'll need to teach
     * decoders a default empty value for a type. This method cheaply supports typical types by only
     * looking at the raw type (vs type hierarchy). Decorate for sophistication.
     */
    static Object emptyValueOf(Type type) {
        return EMPTIES.get(Types.getRawType(type));
    }

    private static final Map<Class<?>, Object> EMPTIES;

    static {
        Map<Class<?>, Object> empties = new LinkedHashMap<Class<?>, Object>();
        empties.put(boolean.class, false);
        empties.put(Boolean.class, false);
        empties.put(byte[].class, new byte[0]);
        empties.put(Collection.class, Collections.emptyList());
        empties.put(
                Iterator.class,
                new Iterator<Object>() { // Collections.emptyIterator is a 1.7 api
                    @Override
                    public boolean hasNext() {
                        return false;
                    }

                    @Override
                    public Object next() {
                        throw new NoSuchElementException();
                    }

                    @Override
                    public void remove() {
                        throw new IllegalStateException();
                    }
                });
        empties.put(List.class, Collections.emptyList());
        empties.put(Map.class, Collections.emptyMap());
        empties.put(Set.class, Collections.emptySet());
        EMPTIES = Collections.unmodifiableMap(empties);
    }

    /**
     * Adapted from {@code com.google.common.io.CharStreams.toString()}.
     */
    static String toString(Reader reader) throws IOException {
        if (reader == null) {
            return null;
        }
        try {
            StringBuilder to = new StringBuilder();
            CharBuffer buf = CharBuffer.allocate(BUF_SIZE);
            while (reader.read(buf) != -1) {
                buf.flip();
                to.append(buf);
                buf.clear();
            }
            return to.toString();
        } finally {
            ensureClosed(reader);
        }
    }

    /**
     * Adapted from {@code com.google.common.io.ByteStreams.toByteArray()}.
     */
    static byte[] toByteArray(InputStream in) throws IOException {
        checkNotNull(in, "in");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return out.toByteArray();
        } finally {
            ensureClosed(in);
        }
    }

    /**
     * Adapted from {@code com.google.common.io.ByteStreams.copy()}.
     */
    private static long copy(InputStream from, OutputStream to) throws IOException {
        checkNotNull(from, "from");
        checkNotNull(to, "to");
        byte[] buf = new byte[BUF_SIZE];
        long total = 0;
        while (true) {
            int read = from.read(buf);
            if (read == -1) {
                break;
            }
            to.write(buf, 0, read);
            total += read;
        }
        return total;
    }

    static String decodeOrDefault(byte[] data, Charset charset, String defaultValue) {
        if (data == null) {
            return defaultValue;
        }
        checkNotNull(charset, "charset");
        try {
            return charset.newDecoder().decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException ex) {
            return defaultValue;
        }
    }
}
