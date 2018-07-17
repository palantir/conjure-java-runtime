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

package com.palantir.remoting3.ext.jackson;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Used to enforce strict double values by banning "NaN", "+Infinity", and "-Infinity" for both serialization
 * and deserialization.
 * <p>
 * Jackson does not support serialization/deserialization of strict numbers as of 2.9.6.
 *
 * @see <a href="https://github.com/FasterXML/jackson-databind/issues/911">https://github.com/FasterXML/jackson-databind/issues/911</a> for more details.
 */
final class StrictDoubleModule extends SimpleModule {

    StrictDoubleModule() {
        super(StrictDoubleModule.class.getCanonicalName());

        addDeserializer(Double.class, new StrictDoubleDeserializer(Double.class, null));
        addDeserializer(double.class, new StrictDoubleDeserializer(double.class, 0.d));
        addSerializer(Double.class, new StrictDoubleSerializer(Double.class));
        addSerializer(double.class, new StrictDoubleSerializer(double.class));
    }

    static final class StrictDoubleDeserializer extends NumberDeserializers.DoubleDeserializer {

        StrictDoubleDeserializer(Class<Double> cls, Double nvl) {
            super(cls, nvl);
        }

        @Override
        public Double deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            Double doubleValue = super.deserialize(parser, ctxt);
            if (Double.isInfinite(doubleValue.doubleValue()) || Double.isNaN(doubleValue.doubleValue())) {
                throw new JsonParseException(parser,
                        "NaN or Infinity is not allowed and only concrete double values are allowed.");
            }
            return doubleValue;
        }

        @Override
        public Double deserializeWithType(JsonParser parser, DeserializationContext ctxt,
                TypeDeserializer typeDeserializer) throws IOException {
            return deserialize(parser, ctxt);
        }
    }

    /**
     * Based on {@link com.fasterxml.jackson.databind.ser.std.NumberSerializers.DoubleSerializer}
     * and {@link com.fasterxml.jackson.databind.ser.std.NumberSerializers.Base}.
     */
    static final class StrictDoubleSerializer extends StdScalarSerializer<Double> {
        protected StrictDoubleSerializer(Class<Double> clazz) {
            super(clazz);
        }

        @Override
        public JsonNode getSchema(SerializerProvider provider, Type typeHint) {
            return createSchemaNode("number", true);
        }

        @Override
        public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint)
                throws JsonMappingException {
            visitFloatFormat(visitor, typeHint, JsonParser.NumberType.DOUBLE);
        }

        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (Double.isInfinite(value.doubleValue()) || Double.isNaN(value.doubleValue())) {
                throw new JsonGenerationException(
                        "NaN or Infinity is not allowed and only concrete double values are allowed.", gen);
            }
            gen.writeNumber(value.doubleValue());
        }

        @Override
        public void serializeWithType(Double value, JsonGenerator gen, SerializerProvider provider,
                TypeSerializer typeSer) throws IOException {
            serialize(value, gen, provider);
        }
    }
}
