/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.jackson.JacksonEncoder;
import java.lang.reflect.Type;
import java.util.Collections;

/**
 * Fork of {@link JacksonEncoder} for use with jackson 2.4.
 * <p>
 * This is mostly important for use with Spark: As of 1.5, Spark requires jackson 2.4.4
 * (http://mvnrepository.com/artifact/org.apache.spark/spark-core_2.10/1.5.1).
 * {@link ObjectMapper#writerWithType(Class)} was deprecated in later versions of jackson and feign has fixed this to
 * use the new method, but this means feign requires new versions of jackson. Newer versions of jackson are incompatible
 * with Spark 1.4, 1.5 or 1.6.
 */
public final class Jackson24Encoder implements Encoder {

    private final ObjectMapper mapper;

    public Jackson24Encoder() {
        this(Collections.<Module>emptyList());
    }

    public Jackson24Encoder(Iterable<Module> modules) {
        this(new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .registerModules(modules));
    }

    public Jackson24Encoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) {
        try {
            JavaType javaType = mapper.getTypeFactory().constructType(bodyType);
            template.body(writerWithType(javaType).writeValueAsString(object));
        } catch (JsonProcessingException e) {
            throw new EncodeException(e.getMessage(), e);
        }
    }

    private ObjectWriter writerWithType(JavaType javaType) {
        return mapper.writerWithType(javaType);
    }
}
