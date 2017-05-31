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

package com.palantir.remoting2.jaxrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;
import com.fasterxml.jackson.module.scala.introspect.ScalaAnnotationIntrospector$;
import com.palantir.remoting2.ext.jackson.ObjectMappers;

public final class ScalaObjectMappers {

    private ScalaObjectMappers() {
    }

    public static ObjectMapper newClientObjectMapper() {
        return withScalaSupport(ObjectMappers.newClientObjectMapper());
    }

    public static ObjectMapper newCborClientObjectMapper() {
        return withScalaSupport(ObjectMappers.newCborClientObjectMapper());
    }

    public static ObjectMapper newServerObjectMapper() {
        return withScalaSupport(ObjectMappers.newServerObjectMapper());
    }

    public static ObjectMapper newCborServerObjectMapper() {
        return withScalaSupport(ObjectMappers.newCborServerObjectMapper());
    }

    private static ObjectMapper withScalaSupport(ObjectMapper objectMapper) {
        objectMapper
                .registerModule(new DefaultScalaModule())
                .setAnnotationIntrospector(new AnnotationIntrospectorPair(
                        ScalaAnnotationIntrospector$.MODULE$, new JacksonAnnotationIntrospector()));

        return objectMapper;
    }

}
