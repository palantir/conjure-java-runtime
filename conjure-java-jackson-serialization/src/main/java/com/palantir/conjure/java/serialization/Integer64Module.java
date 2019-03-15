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

package com.palantir.conjure.java.serialization;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.palantir.conjure.java.lib.SafeLong;
import java.io.IOException;

/**
 * Provides support for the Conjure <pre>integer64</pre> type.
 */
final class Integer64Module extends SimpleModule {

    private Integer64Module() {
        super("integer64");
        // Register to both Long.TYPE and Long.class
        this.addSerializer(new LongAsStringSerializer())
                .addSerializer(Long.class, new LongAsStringSerializer())
                .addDeserializer(long.class, new LongAsStringDeserializer())
                .addDeserializer(Long.class, new LongAsStringDeserializer())
                .addSerializer(new SafeLongSerializer());
    }

    static <T extends ObjectMapper> T register(T mapper) {
        // CBOR encoding may represent all numbers using numeric values because it's not constrained by
        // javascript number bounds.
        if (JsonFactory.FORMAT_NAME_JSON.equals(mapper.getFactory().getFormatName())) {
            mapper.registerModule(new Integer64Module());
        }
        return mapper;
    }

    /**
     * {@link LongAsStringSerializer} Writes long values as {@link String strings}, even
     * when {@link JsonGenerator.Feature#WRITE_NUMBERS_AS_STRINGS} is disabled.
     */
    private static final class LongAsStringSerializer extends StdSerializer<Long> {

        private LongAsStringSerializer() {
            super(long.class);
        }

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (!gen.isEnabled(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS)) {
                withNumbersAsStrings(value, gen);
            } else {
                // In the case WRITE_NUMBERS_AS_STRINGS is enabled on this ObjectMapper, the
                // LongAsStringSerializer must not modify state.
                gen.writeNumber(value);
            }
        }

        private static void withNumbersAsStrings(Long value, JsonGenerator gen) throws IOException {
            gen.enable(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS);
            try {
                gen.writeNumber(value);
            } finally {
                gen.disable(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS);
            }
        }
    }

    private static final class LongAsStringDeserializer extends StdDeserializer<Long> {

        private LongAsStringDeserializer() {
            super(Long.TYPE);
        }

        @Override
        public Long deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
            switch (jsonParser.currentToken()) {
                case VALUE_NUMBER_INT:
                    // Lenient implementation for compatibility with existing clients
                    return jsonParser.getLongValue();
                case VALUE_STRING:
                    return Long.valueOf(jsonParser.getValueAsString());
            }
            throw new IOException("Expected a long value");
        }
    }


    /**
     * {@link SafeLongSerializer} is required to avoid writing {@link SafeLong} values as {@link String strings}.
     */
    private static final class SafeLongSerializer extends StdSerializer<SafeLong> {

        private SafeLongSerializer() {
            super(SafeLong.class);
        }

        @Override
        public void serialize(SafeLong value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeNumber(value.longValue());
        }
    }
}
