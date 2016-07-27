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

package com.palantir.remoting.jaxrs.feignimpl;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public final class ObjectMappersTest {
    private static final ObjectMapper MAPPER = ObjectMappers.guavaJdk7();

    @Test
    public void deserializeJdk7ModuleObject() {
        String pathSeparator = File.pathSeparator;
        String json = "\"" + pathSeparator + "tmp" + pathSeparator + "foo.txt\"";

        try {
            assertThat(MAPPER.readValue(json, Path.class), is(Paths.get(":tmp:foo.txt")));
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void serializeJdk7ModuleObject() {
        Path path = Paths.get(":tmp:foo.txt");
        try {
            assertThat(MAPPER.writeValueAsString(path), is("\":tmp:foo.txt\""));
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void testMappersReturnNewInstance() {
        assertNotSame(ObjectMappers.guavaJdk7(), ObjectMappers.guavaJdk7());
        assertNotSame(ObjectMappers.vanilla(), ObjectMappers.vanilla());
    }

}
