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

package com.palantir.remoting1.ext.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Test;

public final class ObjectMappersTest {
    private static final ObjectMapper MAPPER = ObjectMappers.guavaJdk7Jdk8();

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
        assertThat(ObjectMappers.guavaJdk7Jdk8()).isNotSameAs(ObjectMappers.guavaJdk7Jdk8());
    }
}
