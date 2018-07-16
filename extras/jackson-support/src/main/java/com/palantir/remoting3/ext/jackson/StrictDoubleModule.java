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
 */
class StrictDoubleModule extends SimpleModule {

    public StrictDoubleModule() {
        super(StrictDoubleModule.class.getCanonicalName());
        addDeserializer(Double.class, new StrictDoubleDeserializer(Double.class, null));
        addDeserializer(double.class, new StrictDoubleDeserializer(double.class, 0.d));
        addSerializer(Double.class, new StrictDoubleSerializer(Double.class));
        addSerializer(double.class, new StrictDoubleSerializer(double.class));
    }

    static class StrictDoubleDeserializer extends NumberDeserializers.DoubleDeserializer {

        StrictDoubleDeserializer(Class<Double> cls, Double nvl) {
            super(cls, nvl);
        }

        @Override
        public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            Double doubleValue = super.deserialize(p, ctxt);
            if (Double.isInfinite(doubleValue.doubleValue()) || Double.isNaN(doubleValue.doubleValue())) {
                throw new JsonParseException(p,
                        "NaN or Infinity is not allowed and only concrete double value is allowed.");
            }
            return doubleValue;
        }

        @Override
        public Double deserializeWithType(JsonParser p, DeserializationContext ctxt,
                TypeDeserializer typeDeserializer) throws IOException {
            return deserialize(p, ctxt);
        }
    }


    static class StrictDoubleSerializer extends StdScalarSerializer<Double> {
        protected StrictDoubleSerializer(Class<Double> t) {
            super(t);
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
                        "NaN or Infinity is not allowed and only concrete double value is allowed.", gen);
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
