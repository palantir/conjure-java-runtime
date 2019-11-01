/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

public final class ObjectMappers {

    private ObjectMappers() {}

    /**
     * Returns a default ObjectMapper with settings adjusted for use in clients.
     *
     * <p>Settings:
     *
     * <ul>
     *   <li>Ignore unknown properties found during deserialization.
     * </ul>
     */
    public static ObjectMapper newClientObjectMapper() {
        return withDefaultModules(new ObjectMapper()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * Returns a default ObjectMapper which uses the cbor factory with settings adjusted for use in clients.
     *
     * <p>Settings:
     *
     * <ul>
     *   <li>Ignore unknown properties found during deserialization.
     * </ul>
     */
    public static ObjectMapper newCborClientObjectMapper() {
        return withDefaultModules(new ObjectMapper(new CBORFactory()))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * Returns a default ObjectMapper with settings adjusted for use in servers.
     *
     * <p>Settings:
     *
     * <ul>
     *   <li>Throw on unknown properties found during deserialization.
     * </ul>
     */
    public static ObjectMapper newServerObjectMapper() {
        return withDefaultModules(new ObjectMapper()).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * Returns a default ObjectMapper which uses the cbor factory with settings adjusted for use in servers.
     *
     * <p>Settings:
     *
     * <ul>
     *   <li>Throw on unknown properties found during deserialization.
     * </ul>
     */
    public static ObjectMapper newCborServerObjectMapper() {
        return withDefaultModules(new ObjectMapper(new CBORFactory()))
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * Configures provided ObjectMapper with default modules and settings.
     *
     * <p>Modules: Guava, JDK7, JDK8, Afterburner, JavaTime
     *
     * <p>Settings:
     *
     * <ul>
     *   <li>Dates written as ISO-8601 strings.
     *   <li>Dates remain in received timezone.
     *   <li>Exceptions will not be wrapped with Jackson exceptions.
     *   <li>Deserializing a null for a primitive field will throw an exception.
     * </ul>
     */
    public static ObjectMapper withDefaultModules(ObjectMapper mapper) {
        return mapper.registerModule(new GuavaModule())
                .registerModule(new ShimJdk7Module())
                .registerModule(new Jdk8Module().configureAbsentsAsNulls(true))
                .registerModule(new AfterburnerModule())
                .registerModule(new JavaTimeModule())
                .registerModule(new LenientLongModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DeserializationFeature.WRAP_EXCEPTIONS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
