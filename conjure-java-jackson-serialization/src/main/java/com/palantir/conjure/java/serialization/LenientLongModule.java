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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.palantir.logsafe.exceptions.SafeIoException;
import java.io.IOException;

/**
 * Provides support for the {@link Long} deserialization from JSON string and numeric values regardless of
 *
 * <pre>MapperFeature.ALLOW_COERCION_OF_SCALARS</pre>
 *
 * configuration.
 */
final class LenientLongModule extends SimpleModule {

    LenientLongModule() {
        super("lenient long");
        // Register to both Long.TYPE and Long.class
        this.addDeserializer(long.class, new LongAsStringDeserializer())
                .addDeserializer(Long.class, new LongAsStringDeserializer());
    }

    private static final class LongAsStringDeserializer extends StdDeserializer<Long> {

        private LongAsStringDeserializer() {
            super(Long.TYPE);
        }

        @Override
        public Long deserialize(JsonParser jsonParser, DeserializationContext _ctxt) throws IOException {
            switch (jsonParser.currentToken()) {
                case VALUE_NUMBER_INT:
                    return jsonParser.getLongValue();
                case VALUE_STRING:
                    return parseLong(jsonParser);
                case VALUE_NULL:
                    return null;
            }
            throw new SafeIoException("Expected a long value");
        }

        @Override
        public boolean isCachable() {
            return true;
        }

        private static Long parseLong(JsonParser jsonParser) throws IOException {
            try {
                return Long.valueOf(jsonParser.getValueAsString());
            } catch (NumberFormatException e) {
                throw new JsonParseException(jsonParser, "not a valid long value", e);
            }
        }
    }
}
