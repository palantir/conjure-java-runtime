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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codahale.metrics.Histogram;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.InternCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.palantir.logsafe.Preconditions;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ObjectMappersTest {
    private static final JsonMapper MAPPER = ObjectMappers.newClientJsonMapper();

    @Test
    public void deserializeJdk7ModuleObject() throws IOException {
        String pathSeparator = File.pathSeparator;
        String json = "\"" + pathSeparator + "tmp" + pathSeparator + "foo.txt\"";

        assertThat(MAPPER.readValue(json, Path.class)).isEqualTo(Paths.get(":tmp:foo.txt"));
    }

    @Test
    public void serializeJdk7ModuleObject() throws JsonProcessingException {
        Path path = Paths.get(":tmp:foo.txt");
        assertThat(MAPPER.writeValueAsString(path)).isEqualTo("\":tmp:foo.txt\"");
    }

    @Test
    public void deserializeJdk8ModulePresentOptional() throws IOException {
        assertThat(MAPPER.readValue("\"Test\"", Optional.class)).contains("Test");
    }

    @Test
    public void deserializeJdk8ModuleAbsentOptional() throws IOException {
        assertThat(MAPPER.readValue("null", Optional.class)).isNotPresent();
    }

    @Test
    public void serializeJdk8ModulePresentOptional() throws JsonProcessingException {
        assertThat(MAPPER.writeValueAsString(Optional.of("Test"))).isEqualTo("\"Test\"");
    }

    @Test
    public void serializeJdk8ModuleEmptyOptional() throws JsonProcessingException {
        assertThat(MAPPER.writeValueAsString(Optional.empty())).isEqualTo("null");
    }

    @Test
    public void testMappersReturnNewInstance() {
        assertThat(ObjectMappers.newClientJsonMapper()).isNotSameAs(ObjectMappers.newClientJsonMapper());
    }

    @Test
    public void testJdk8DateTimeSerialization() throws IOException {
        Duration duration = Duration.ofMinutes(60 * 50 + 1); // 50h, 1min
        assertThat(ser(duration)).isEqualTo("\"PT50H1M\"");
        assertThat(serDe(duration, Duration.class)).isEqualTo(duration);

        OffsetDateTime offsetDateTime = OffsetDateTime.of(2001, 2, 3, 4, 5, 6, 7, ZoneOffset.ofHours(-5));
        assertThat(ser(offsetDateTime)).isEqualTo("\"2001-02-03T04:05:06.000000007-05:00\"");
        assertThat(serDe(offsetDateTime, OffsetDateTime.class)).isEqualTo(offsetDateTime);

        ZonedDateTime zoneDateTime = ZonedDateTime.of(2001, 2, 3, 4, 5, 6, 7, ZoneId.of(ZoneId.SHORT_IDS.get("EST")));
        assertThat(ser(zoneDateTime)).isEqualTo("\"2001-02-03T04:05:06.000000007-05:00\"");
        assertThat(serDe(zoneDateTime, ZonedDateTime.class)).isEqualTo(zoneDateTime);

        LocalDate localDate = LocalDate.of(2001, 2, 3);
        assertThat(ser(localDate)).isEqualTo("\"2001-02-03\"");
        assertThat(serDe(localDate, LocalDate.class)).isEqualTo(localDate);
    }

    @Test
    public void testMapWithNullValues() throws IOException {
        // This is potentially a bug, see conjure-java#291
        assertThat(MAPPER.<Map<String, String>>readValue(
                        "{\"test\":null}", new TypeReference<Map<String, String>>() {}))
                .containsExactlyInAnyOrderEntriesOf(Collections.singletonMap("test", null));
    }

    @Test
    public void testMapWithNullKeys() {
        assertThatThrownBy(() -> MAPPER.readValue("{null: \"test\"}", new TypeReference<Map<String, String>>() {}))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testLongDeserializationFromString() throws IOException {
        assertThat(MAPPER.readValue("\"1\"", Long.class)).isEqualTo(1L);
    }

    @Test
    public void testLongTypeDeserializationFromString() throws IOException {
        assertThat(MAPPER.readValue("\"1\"", Long.TYPE)).isEqualTo(1L);
    }

    @Test
    public void testLongBeanTypeDeserializationFromString() throws IOException {
        assertThat(MAPPER.readValue("{\"value\":\"1\"}", LongBean.class)).isEqualTo(new LongBean(1L));
    }

    @Test
    public void testLongBeanTypeDeserializationFromNumber() throws IOException {
        assertThat(MAPPER.readValue("{\"value\":\"1\"}", LongBean.class)).isEqualTo(new LongBean(1L));
    }

    static final class LongBean {
        @JsonProperty
        private long value;

        LongBean() {}

        LongBean(long value) {
            setValue(value);
        }

        public long getValue() {
            return value;
        }

        public void setValue(long value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            LongBean that = (LongBean) other;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(value);
        }

        @Override
        public String toString() {
            return "LongBean{value=" + value + '}';
        }
    }

    @Test
    public void testOptionalLongTypeDeserializationFromString() throws IOException {
        assertThat(MAPPER.readValue("\"1\"", OptionalLong.class)).hasValue(1L);
    }

    @Test
    public void testOptionalLongBeanTypeDeserializationFromString() throws IOException {
        assertThat(MAPPER.readValue("{\"value\":\"1\"}", OptionalLongBean.class))
                .isEqualTo(new OptionalLongBean(OptionalLong.of(1L)));
    }

    @Test
    public void testOptionalLongBeanTypeDeserializationFromNumber() throws IOException {
        assertThat(MAPPER.readValue("{\"value\":1}", OptionalLongBean.class))
                .isEqualTo(new OptionalLongBean(OptionalLong.of(1L)));
    }

    static final class OptionalLongBean {
        @JsonProperty
        private OptionalLong value = OptionalLong.empty();

        OptionalLongBean() {}

        OptionalLongBean(OptionalLong value) {
            setValue(value);
        }

        public OptionalLong getValue() {
            return value;
        }

        public void setValue(OptionalLong value) {
            this.value = Preconditions.checkNotNull(value, "value");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            OptionalLongBean that = (OptionalLongBean) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return "OptionalLongBean{value=" + value + '}';
        }
    }

    @Test
    public void testLongDeserializationFromJsonNumber() throws IOException {
        assertThat(MAPPER.readValue("1", Long.class)).isEqualTo(1L);
    }

    @Test
    public void testOptionalLongDeserializationFromJsonNumber() throws IOException {
        assertThat(MAPPER.readValue("1", OptionalLong.class)).hasValue(1L);
    }

    @Test
    public void testLongTypeDeserializationFromJsonNumber() throws IOException {
        assertThat(MAPPER.readValue("1", Long.TYPE)).isEqualTo(1L);
    }

    @Test
    public void testLongDeserializationFromJsonNull() throws IOException {
        assertThat(MAPPER.readValue("null", Long.class)).isNull();
    }

    @Test
    public void testOptionalLongDeserializationFromJsonNull() throws IOException {
        assertThat(MAPPER.readValue("null", OptionalLong.class)).isEmpty();
    }

    @Test
    public void testLongOverflowDeserialization() {
        BigInteger large = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThatThrownBy(() -> MAPPER.readValue("" + large, Long.TYPE))
                .isInstanceOf(InputCoercionException.class)
                .hasMessageContaining("out of range of long");
    }

    @Test
    public void testLongAsStringOverflowDeserialization() {
        BigInteger large = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThatThrownBy(() -> MAPPER.readValue("\"" + large + "\"", Long.TYPE))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("not a valid");
    }

    @Test
    public void testOptionalLongOverflowDeserialization() {
        BigInteger large = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThatThrownBy(() -> MAPPER.readValue("" + large, OptionalLong.class))
                .isInstanceOf(InputCoercionException.class)
                .hasMessageContaining("out of range of long");
    }

    @Test
    public void testOptionalLongAsStringOverflowDeserialization() {
        BigInteger large = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThatThrownBy(() -> MAPPER.readValue("\"" + large + "\"", OptionalLong.class))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("not a valid");
    }

    @Test
    public void testIntegerOverflowDeserialization() {
        assertThatThrownBy(() -> MAPPER.readValue("" + Long.MAX_VALUE, Integer.TYPE))
                .isInstanceOf(InputCoercionException.class)
                .hasMessageContaining("out of range of int");
    }

    @Test
    public void testOptionalIntOverflowDeserialization() {
        assertThatThrownBy(() -> MAPPER.readValue("" + Long.MAX_VALUE, OptionalInt.class))
                .isInstanceOf(InputCoercionException.class)
                .hasMessageContaining("out of range of int");
    }

    @Test
    public void testStringMetrics_json() throws IOException {
        TaggedMetricRegistry registry = SharedTaggedMetricRegistries.getSingleton();
        removeJsonParserMetrics(registry);
        Histogram stringLength = JsonParserMetrics.of(registry).stringLength(JsonFactory.FORMAT_NAME_JSON);
        assertThat(stringLength.getSnapshot().size()).isZero();
        // Length must exceed the minimum threshold for metrics
        String expected = "Hello, World!".repeat(100000);
        String value = ObjectMappers.newServerJsonMapper().readValue("\"" + expected + "\"", String.class);
        assertThat(value).isEqualTo(expected);
        assertThat(stringLength.getSnapshot().size()).isOne();
        assertThat(stringLength.getSnapshot().getMax()).isEqualTo(expected.length());
    }

    @Test
    public void testStringMetricsNotRecordedWhenValuesAreSmall_json() throws IOException {
        TaggedMetricRegistry registry = SharedTaggedMetricRegistries.getSingleton();
        removeJsonParserMetrics(registry);
        Histogram stringLength = JsonParserMetrics.of(registry).stringLength(JsonFactory.FORMAT_NAME_JSON);
        assertThat(stringLength.getSnapshot().size()).isZero();
        String expected = "Hello, World!";
        String value = ObjectMappers.newServerJsonMapper().readValue("\"" + expected + "\"", String.class);
        assertThat(value).isEqualTo(expected);
        assertThat(stringLength.getSnapshot().size()).isZero();
    }

    @Test
    public void testStringMetrics_smile() throws IOException {
        TaggedMetricRegistry registry = SharedTaggedMetricRegistries.getSingleton();
        removeJsonParserMetrics(registry);
        Histogram stringLength = JsonParserMetrics.of(registry).stringLength(SmileFactory.FORMAT_NAME_SMILE);
        assertThat(stringLength.getSnapshot().size()).isZero();
        // Length must exceed the minimum threshold for metrics
        String expected = "Hello, World!".repeat(100000);
        String value = ObjectMappers.newServerSmileMapper()
                .readValue(ObjectMappers.newClientSmileMapper().writeValueAsBytes(expected), String.class);
        assertThat(value).isEqualTo(expected);
        assertThat(stringLength.getSnapshot().size()).isOne();
        assertThat(stringLength.getSnapshot().getMax()).isEqualTo(expected.length());
    }

    @Test
    public void testStringMetricsNotRecordedWhenValuesAreSmall_smile() throws IOException {
        TaggedMetricRegistry registry = SharedTaggedMetricRegistries.getSingleton();
        removeJsonParserMetrics(registry);
        Histogram stringLength = JsonParserMetrics.of(registry).stringLength(SmileFactory.FORMAT_NAME_SMILE);
        assertThat(stringLength.getSnapshot().size()).isZero();
        String expected = "Hello, World!";
        String value = ObjectMappers.newServerSmileMapper()
                .readValue(ObjectMappers.newClientSmileMapper().writeValueAsBytes(expected), String.class);
        assertThat(value).isEqualTo(expected);
        assertThat(stringLength.getSnapshot().size()).isZero();
    }

    private static void removeJsonParserMetrics(TaggedMetricRegistry registry) {
        // Unregister relevant metrics
        registry.forEachMetric((name, _value) -> {
            if (name.safeName().startsWith("json.parser")) {
                registry.remove(name);
            }
        });
    }

    @Test
    public void testJsonFormatName() {
        assertThat(ObjectMappers.newServerJsonMapper().getFactory().getFormatName())
                .isEqualTo("JSON");
        assertThat(ObjectMappers.newClientJsonMapper().getFactory().getFormatName())
                .isEqualTo("JSON");
    }

    @Test
    public void testCborFormatName() {
        assertThat(ObjectMappers.newServerCborMapper().getFactory().getFormatName())
                .isEqualTo("CBOR");
        assertThat(ObjectMappers.newClientCborMapper().getFactory().getFormatName())
                .isEqualTo("CBOR");
    }

    @Test
    public void testSmileFormatName() {
        assertThat(ObjectMappers.newServerSmileMapper().getFactory().getFormatName())
                .isEqualTo("Smile");
        assertThat(ObjectMappers.newClientSmileMapper().getFactory().getFormatName())
                .isEqualTo("Smile");
    }

    @Test
    public void testTypeFactoryCache() {
        testTypeFactory(ObjectMappers.newServerObjectMapper());
        testTypeFactory(ObjectMappers.newClientObjectMapper());
        testTypeFactory(ObjectMappers.newServerJsonMapper());
        testTypeFactory(ObjectMappers.newClientJsonMapper());
        testTypeFactory(ObjectMappers.newSmileServerObjectMapper());
        testTypeFactory(ObjectMappers.newSmileClientObjectMapper());
        testTypeFactory(ObjectMappers.newServerSmileMapper());
        testTypeFactory(ObjectMappers.newClientSmileMapper());
        testTypeFactory(ObjectMappers.newCborServerObjectMapper());
        testTypeFactory(ObjectMappers.newCborClientObjectMapper());
        testTypeFactory(ObjectMappers.newServerCborMapper());
        testTypeFactory(ObjectMappers.newClientCborMapper());
    }

    private void testTypeFactory(ObjectMapper mapper) {
        assertThat(mapper.getTypeFactory()).isInstanceOf(NonCachingTypeFactory.class);
    }

    @Test
    public void testObjectMapperWithDefaultModules() throws IOException {
        ObjectMapper mapper = ObjectMappers.withDefaultModules(new ObjectMapper().registerModule(new Jdk8Module()));
        Optional<String> value = mapper.readValue("\"hello\"", new TypeReference<>() {});
        assertThat(value).hasValue("hello");
    }

    @Test
    public void testJsonMapperBuilderWithDefaultModules() throws IOException {
        ObjectMapper mapper = ObjectMappers.withDefaultModules(
                        JsonMapper.builder().addModule(new Jdk8Module().configureAbsentsAsNulls(true)))
                .build();
        Optional<String> value = mapper.readValue("\"hello\"", new TypeReference<>() {});
        assertThat(value).hasValue("hello");
    }

    @Test
    public void testObjectMapperWithDefaultModulesRetainsTypeFactoryClassLoader() throws IOException {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[0])) {
            ObjectMapper mapper = new ObjectMapper()
                    .setTypeFactory(TypeFactory.defaultInstance().withClassLoader(classLoader));
            ObjectMapper updated = ObjectMappers.withDefaultModules(mapper);
            assertThat(updated.getTypeFactory().getClassLoader()).isSameAs(classLoader);
        }

        ObjectMapper mapper = ObjectMappers.withDefaultModules(ObjectMappers.newClientJsonMapper());
        Optional<String> value = mapper.readValue("\"hello\"", new TypeReference<>() {});
        assertThat(value).hasValue("hello");
    }

    @Test
    public void testJsonMapperBuilderWithDefaultModulesRetainsTypeFactoryClassLoader() throws IOException {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[0])) {
            JsonMapper mapper = ObjectMappers.withDefaultModules(JsonMapper.builder()
                            .typeFactory(TypeFactory.defaultInstance().withClassLoader(classLoader)))
                    .build();
            ObjectMapper updated = ObjectMappers.withDefaultModules(mapper);
            assertThat(updated.getTypeFactory().getClassLoader()).isSameAs(classLoader);
        }

        ObjectMapper mapper = ObjectMappers.withDefaultModules(ObjectMappers.newClientJsonMapper());
        Optional<String> value = mapper.readValue("\"hello\"", new TypeReference<>() {});
        assertThat(value).hasValue("hello");
    }

    @Test
    public void testMapKeysAreNotInterned() throws IOException {
        testMapKeysAreNotInterned(ObjectMappers.newServerJsonMapper());
        testMapKeysAreNotInterned(ObjectMappers.newServerCborMapper());
        testMapKeysAreNotInterned(ObjectMappers.newServerSmileMapper());
    }

    private void testMapKeysAreNotInterned(ObjectMapper mapper) throws IOException {
        Map<String, String> expected = Collections.singletonMap(
                UUID.randomUUID().toString(), UUID.randomUUID().toString());
        byte[] serialized = mapper.writeValueAsBytes(expected);
        // Reset static state cache. Note that this may flake if we run tests in parallel within
        // the same process.
        InternCache.instance.clear();
        Map<String, String> actual = mapper.readValue(serialized, new TypeReference<>() {});
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(InternCache.instance)
                .as("The Jackson InternCache should have no interactions "
                        + "due to pitfalls described in https://shipilev.net/jvm/anatomy-quarks/10-string-intern/")
                .isEmpty();
    }

    @Test
    public void testExtraordinarilyLargeStrings() throws IOException {
        // Value must sit between StreamReadConstraints.DEFAULT_MAX_STRING_LEN and
        // ReflectiveStreamReadConstraints.MAX_STRING_LENGTH.
        int size = 10_000_000;
        String parsed = ObjectMappers.newServerJsonMapper().readValue('"' + "a".repeat(size) + '"', String.class);
        assertThat(parsed).hasSize(size);
    }

    private static String ser(Object object) throws IOException {
        return MAPPER.writeValueAsString(object);
    }

    private static <T> T serDe(Object object, Class<T> clazz) throws IOException {
        return MAPPER.readValue(ser(object), clazz);
    }
}
