/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class BinaryBytesDeserializationModuleTest {

    private ObjectMapper smileMapper;

    @BeforeEach
    void before() {
        smileMapper = ObjectMappers.newSmileClientObjectMapper();
        smileMapper.registerModule(new BinaryBytesDeserializationModule());
    }

    @Test
    void deserializesRawByteArrayToBytes() throws IOException {
        byte[] value = {1, 2, 3, 4};
        byte[] serialized = smileMapper.writeValueAsBytes(value);

        Object deserialized = smileMapper.readValue(serialized, Object.class);
        assertThat(deserialized).isInstanceOf(Bytes.class).isEqualTo(Bytes.from(value));
    }

    @Test
    void deserializesBytesNestedInMap() throws IOException {
        Map<Object, Object> object =
                Map.of("foo", "bar", "num", 2, "obj", Map.of("bytes", Bytes.from(new byte[] {1, 2, 3})));
        byte[] serialized = smileMapper.writeValueAsBytes(object);

        Object deserialized = smileMapper.readValue(serialized, Object.class);
        assertThat(deserialized).isEqualTo(object);
    }

    @Test
    void deserializesNestedObjectFields() throws IOException {
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
        byte[] value = {1, 2, 3, 4};
        byte[] serialized = smileMapper.writeValueAsBytes(value);

        byte[] deserialized = smileMapper.readValue(serialized, byte[].class);
        assertThat(deserialized).isInstanceOf(byte[].class).containsExactly(value);
    }

    @Test
    void deserializesCborBinaryAsBytes() throws IOException {
        ObjectMapper cborMapper = ObjectMappers.newCborClientObjectMapper();
        cborMapper.registerModule(new BinaryBytesDeserializationModule());

        byte[] value = {1, 2, 3, 4};
        byte[] serialized = cborMapper.writeValueAsBytes(value);

        Object deserialized = cborMapper.readValue(serialized, Object.class);
        assertThat(deserialized).isInstanceOf(Bytes.class).isEqualTo(Bytes.from(value));
    }

    @Test
    void deserializesYamlBinaryAsBytes() throws IOException {
        ObjectMapper yamlMapper = YAMLMapper.builder().build();
        yamlMapper.registerModule(new BinaryBytesDeserializationModule());

        byte[] value = new byte[] {1, 2, 3};
        String base64Value = new String(Base64.getEncoder().encode(value), StandardCharsets.UTF_8);
        String yaml = "testData: !!binary " + base64Value;

        Object deserialized = yamlMapper.readValue(yaml, Object.class);
        assertThat(deserialized).isInstanceOf(Map.class);
        Map<?, ?> deserializedMap = (Map<?, ?>) deserialized;
        assertThat(deserializedMap.get("testData")).isInstanceOf(Bytes.class).isEqualTo(Bytes.from(value));
    }

    @SuppressWarnings({"DangerousRecordArrayField", "ArrayRecordComponent"}) // Intentional
    private record RecordContainingObject(Object obj, byte[] byteArray, Bytes bytes) {}
}
