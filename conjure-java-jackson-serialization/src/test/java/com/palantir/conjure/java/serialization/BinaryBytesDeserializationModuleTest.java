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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.palantir.conjure.java.lib.Bytes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class BinaryBytesDeserializationModuleTest {

    @Test
    void deserializesRawByteArrayToBytes() throws IOException {
        ObjectMapper smileMapper = withBinaryDeserializationModule(ObjectMappers.newSmileClientObjectMapper());

        byte[] value = {1, 2, 3, 4};
        byte[] serialized = smileMapper.writeValueAsBytes(value);

        Object deserialized = smileMapper.readValue(serialized, Object.class);
        assertThat(deserialized).isInstanceOf(Bytes.class).isEqualTo(Bytes.from(value));
    }

    @Test
    void deserializesBytesNestedInMap() throws IOException {
        ObjectMapper smileMapper = withBinaryDeserializationModule(ObjectMappers.newSmileClientObjectMapper());

        Map<Object, Object> object =
                Map.of("foo", "bar", "num", 2, "obj", Map.of("bytes", Bytes.from(new byte[] {1, 2, 3})));
        byte[] serialized = smileMapper.writeValueAsBytes(object);

        Object deserialized = smileMapper.readValue(serialized, Object.class);
        assertThat(deserialized).isEqualTo(object);
    }

    @Test
    void deserializesNestedObjectFields() throws IOException {
        @SuppressWarnings({"DangerousRecordArrayField", "ArrayRecordComponent"}) // Intentional
        record RecordContainingObject(Object obj, byte[] byteArray, Bytes bytes) {}

        ObjectMapper smileMapper = withBinaryDeserializationModule(ObjectMappers.newSmileClientObjectMapper());

        RecordContainingObject obj = new RecordContainingObject(
                new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, Bytes.from(new byte[] {7, 8, 9}));
        byte[] serialized = smileMapper.writeValueAsBytes(obj);

        RecordContainingObject deserialized = smileMapper.readValue(serialized, RecordContainingObject.class);
        assertThat(deserialized.obj()).isInstanceOf(Bytes.class).isEqualTo(Bytes.from(new byte[] {1, 2, 3}));
        assertThat(deserialized.byteArray()).containsExactly(obj.byteArray());
        assertThat(deserialized.bytes()).isEqualTo(obj.bytes());
    }

    @Test
    void deserializedBinaryUuidsAreEqual() throws IOException {
        ObjectMapper smileMapper = withBinaryDeserializationModule(ObjectMappers.newSmileClientObjectMapper());

        // UUIDs are serialized as binary by default, and are the case where we have observed equality issues without
        // this module
        Set<UUID> uuids = Set.of(new UUID(0, 1), new UUID(2, 3), new UUID(4, 5));
        byte[] serialized = smileMapper.writeValueAsBytes(uuids);

        Object first = smileMapper.readValue(serialized, Object.class);
        Object second = smileMapper.readValue(serialized, Object.class);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void deserializesToByteArrayWhenSpecificallyRequested() throws IOException {
        ObjectMapper smileMapper = withBinaryDeserializationModule(ObjectMappers.newSmileClientObjectMapper());

        byte[] value = {1, 2, 3, 4};
        byte[] serialized = smileMapper.writeValueAsBytes(value);

        byte[] deserialized = smileMapper.readValue(serialized, byte[].class);
        assertThat(deserialized).isInstanceOf(byte[].class).containsExactly(value);
    }

    @Test
    void deserializesCborBinaryAsBytes() throws IOException {
        ObjectMapper cborMapper = withBinaryDeserializationModule(ObjectMappers.newCborClientObjectMapper());

        byte[] value = {1, 2, 3, 4};
        byte[] serialized = cborMapper.writeValueAsBytes(value);

        Object deserialized = cborMapper.readValue(serialized, Object.class);
        assertThat(deserialized).isInstanceOf(Bytes.class).isEqualTo(Bytes.from(value));
    }

    @Test
    void deserializesYamlBinaryAsBytes() throws IOException {
        record TestData(Object testData) {}

        ObjectMapper yamlMapper =
                withBinaryDeserializationModule(YAMLMapper.builder().build());

        byte[] value = new byte[] {1, 2, 3};
        String base64Value = new String(Base64.getEncoder().encode(value), StandardCharsets.UTF_8);
        String yaml = "testData: !!binary " + base64Value;

        TestData deserialized = yamlMapper.readValue(yaml, TestData.class);
        assertThat(deserialized.testData()).isInstanceOf(Bytes.class).isEqualTo(Bytes.from(value));
    }

    @Test
    void doesNotAffectJsonDeserialization() throws IOException {
        ObjectMapper jsonMapper = withBinaryDeserializationModule(ObjectMappers.newClientObjectMapper());

        byte[] value = {1, 2, 3, 4};
        byte[] serialized = jsonMapper.writeValueAsBytes(value);

        // Binary data should be written to JSON as a base64 string, and should deserialize to that string
        Object deserialized = jsonMapper.readValue(serialized, Object.class);
        assertThat(deserialized)
                .isInstanceOf(String.class)
                .isEqualTo(new String(Base64.getEncoder().encode(value), StandardCharsets.UTF_8));
    }

    private ObjectMapper withBinaryDeserializationModule(ObjectMapper rawMapper) {
        return rawMapper.registerModule(new BinaryBytesDeserializationModule());
    }
}
