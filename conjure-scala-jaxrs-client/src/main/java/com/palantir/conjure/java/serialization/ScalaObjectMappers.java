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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;
import com.fasterxml.jackson.module.scala.introspect.ScalaAnnotationIntrospector$;

public final class ScalaObjectMappers {

    private ScalaObjectMappers() {}

    public static JsonMapper newClientJsonMapper() {
        return withScalaSupport(ObjectMappers.newClientJsonMapper());
    }

    public static CBORMapper newClientCborMapper() {
        return withScalaSupport(ObjectMappers.newClientCborMapper());
    }

    public static SmileMapper newClientSmileMapper() {
        return withScalaSupport(ObjectMappers.newClientSmileMapper());
    }

    public static JsonMapper newServerJsonMapper() {
        return withScalaSupport(ObjectMappers.newServerJsonMapper());
    }

    public static CBORMapper newServerCborMapper() {
        return withScalaSupport(ObjectMappers.newServerCborMapper());
    }

    public static SmileMapper newServerSmileMapper() {
        return withScalaSupport(ObjectMappers.newServerSmileMapper());
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

    private static <T extends ObjectMapper> T withScalaSupport(T objectMapper) {
        objectMapper
                .registerModule(new DefaultScalaModule())
                .setAnnotationIntrospector(new AnnotationIntrospectorPair(
                        ScalaAnnotationIntrospector$.MODULE$, new JacksonAnnotationIntrospector()));

        return objectMapper;
    }
}
