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

package com.palantir.remoting2.ext.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.Test;

public final class ObjectMappersTest {
    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();

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
        assertThat(MAPPER.readValue("\"Test\"", Optional.class)).isEqualTo(Optional.of("Test"));
    }

    @Test
    public void deserializeJdk8ModuleAbsentOptional() throws IOException {
        assertThat(MAPPER.readValue("null", Optional.class)).isEqualTo(Optional.empty());
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
        assertThat(ObjectMappers.newClientObjectMapper()).isNotSameAs(ObjectMappers.newClientObjectMapper());
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

    private static String ser(Object object) throws IOException {
        return MAPPER.writeValueAsString(object);
    }

    private static <T> T serDe(Object object, Class<T> clazz) throws IOException {
        return MAPPER.readValue(ser(object), clazz);
    }
}
