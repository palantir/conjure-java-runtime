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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.TSFBuilder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.palantir.conjure.java.jackson.optimizations.ObjectMapperOptimizations;

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
    public static JsonMapper newClientJsonMapper() {
        return withDefaultModules(JsonMapper.builder(jsonFactory()))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
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
    public static CBORMapper newClientCborMapper() {
        return withDefaultModules(CBORMapper.builder(cborFactory()))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * Returns a default ObjectMapper which uses the cbor factory with settings adjusted for use in clients.
     *
     * <p>Settings:
     *
     * <ul>
     *   <li>Disable 7-bit binary encoding.
     *   <li>Ignore unknown properties found during deserialization.
     * </ul>
     */
    public static SmileMapper newClientSmileMapper() {
        return withDefaultModules(SmileMapper.builder(smileFactory()))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
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
    public static JsonMapper newServerJsonMapper() {
        return withDefaultModules(JsonMapper.builder(jsonFactory()))
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
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
    public static CBORMapper newServerCborMapper() {
        return withDefaultModules(CBORMapper.builder(cborFactory()))
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * Returns a default ObjectMapper which uses the smile factory with settings adjusted for use in servers.
     *
     * <p>Settings:
     *
     * <ul>
     *   <li>Disable 7-bit binary encoding.
     *   <li>Throw on unknown properties found during deserialization.
     * </ul>
     */
    public static SmileMapper newServerSmileMapper() {
        return withDefaultModules(SmileMapper.builder(smileFactory()))
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public static ObjectMapper newClientObjectMapper() {
        return newClientJsonMapper();
    }

    public static ObjectMapper newCborClientObjectMapper() {
        return newClientCborMapper();
    }

    public static ObjectMapper newSmileClientObjectMapper() {
        return newClientSmileMapper();
    }

    public static ObjectMapper newServerObjectMapper() {
        return newServerJsonMapper();
    }

    public static ObjectMapper newCborServerObjectMapper() {
        return newServerCborMapper();
    }

    public static ObjectMapper newSmileServerObjectMapper() {
        return newServerSmileMapper();
    }

    /**
     * Configures provided MapperBuilder with default modules and settings.
     *
     * <p>Modules: Guava, JDK7, JDK8, Afterburner, JavaTime, Joda
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
    public static <M extends ObjectMapper, B extends MapperBuilder<M, B>> B withDefaultModules(B builder) {
        return builder.typeFactory(NonCachingTypeFactory.from(builder.build().getTypeFactory()))
                .addModule(new GuavaModule())
                .addModule(new ShimJdk7Module())
                .addModule(new Jdk8Module().configureAbsentsAsNulls(true))
                .addModules(ObjectMapperOptimizations.createModules())
                .addModule(new JavaTimeModule())
                .addModule(new LenientLongModule())
                // we strongly recommend using built-in java.time classes instead of joda ones. Joda deserialization
                // was implicit up until jackson 2.12
                .addModule(new JodaModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DeserializationFeature.WRAP_EXCEPTIONS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }

    /**
     * Configures provided ObjectMapper with default modules and settings.
     *
     * <p>Modules: Guava, JDK7, JDK8, Afterburner, JavaTime, Joda
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
        return mapper.setTypeFactory(NonCachingTypeFactory.from(mapper.getTypeFactory()))
                .registerModule(new GuavaModule())
                .registerModule(new ShimJdk7Module())
                .registerModule(new Jdk8Module().configureAbsentsAsNulls(true))
                .registerModules(ObjectMapperOptimizations.createModules())
                .registerModule(new JavaTimeModule())
                .registerModule(new LenientLongModule())
                // we strongly recommend using built-in java.time classes instead of joda ones. Joda deserialization
                // was implicit up until jackson 2.12
                .registerModule(new JodaModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DeserializationFeature.WRAP_EXCEPTIONS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }

    /** Creates a new {@link JsonFactory} configured with Conjure defaults. */
    public static JsonFactory jsonFactory() {
        return withDefaults(InstrumentedJsonFactory.builder()).build();
    }

    /** Creates a new {@link SmileFactory} configured with Conjure defaults. */
    public static SmileFactory smileFactory() {
        return withDefaults(InstrumentedSmileFactory.builder().disable(SmileGenerator.Feature.ENCODE_BINARY_AS_7BIT))
                .build();
    }

    /** Creates a new {@link CBORFactory} configured with Conjure defaults. */
    public static CBORFactory cborFactory() {
        return withDefaults(CBORFactory.builder()).build();
    }

    /** Configures provided JsonFactory with Conjure default settings. */
    private static <F extends JsonFactory, B extends TSFBuilder<F, B>> B withDefaults(B builder) {
        return ReflectiveStreamReadConstraints.withDefaultConstraints(builder
                // Interning introduces excessive contention https://github.com/FasterXML/jackson-core/issues/946
                .disable(JsonFactory.Feature.INTERN_FIELD_NAMES)
                // Canonicalization can be helpful to avoid string re-allocation, however we expect unbounded
                // key space due to use of maps keyed by random identifiers, which cause heavy heap churn.
                // See this discussion: https://github.com/FasterXML/jackson-benchmarks/pull/6
                .disable(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES));
    }
}
