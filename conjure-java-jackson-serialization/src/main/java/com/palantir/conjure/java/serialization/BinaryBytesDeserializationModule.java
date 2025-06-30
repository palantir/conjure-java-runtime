/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.palantir.conjure.java.lib.Bytes;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * Jackson module to deserialize binary data into {@link Bytes} rather than {@code byte[]} when the target type is
 * {@link Object}, which guarantees immutability and implements equality correctly.
 */
public final class BinaryBytesDeserializationModule extends SimpleModule {

    public BinaryBytesDeserializationModule() {
        super(BinaryBytesDeserializationModule.class.getCanonicalName());

        addDeserializer(Object.class, new BytesObjectDeserializer());
    }

    private static final class BytesObjectDeserializer extends UntypedObjectDeserializer {
        BytesObjectDeserializer() {
            // Use the default behaviour for lists and maps
            super(null, (JavaType) null);
        }

        @Override
        public Object deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            Bytes maybeResult = maybeDeserializeEmbeddedByteArray(parser);
            if (maybeResult != null) {
                return maybeResult;
            }
            return super.deserialize(parser, ctxt);
        }

        @Override
        public Object deserialize(JsonParser parser, DeserializationContext ctxt, Object intoValue) throws IOException {
            Bytes maybeResult = maybeDeserializeEmbeddedByteArray(parser);
            if (maybeResult != null) {
                return maybeResult;
            }
            return super.deserialize(parser, ctxt, intoValue);
        }

        @Override
        public Object deserializeWithType(
                JsonParser parser, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {
            Bytes maybeResult = maybeDeserializeEmbeddedByteArray(parser);
            if (maybeResult != null) {
                return maybeResult;
            }
            return super.deserializeWithType(parser, ctxt, typeDeserializer);
        }

        @Nullable
        private Bytes maybeDeserializeEmbeddedByteArray(JsonParser parser) throws IOException {
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_EMBEDDED_OBJECT) {
                if (parser.getEmbeddedObject() instanceof byte[] embeddedBytes) {
                    return Bytes.from(embeddedBytes);
                }
            }
            return null;
        }
    }
}
