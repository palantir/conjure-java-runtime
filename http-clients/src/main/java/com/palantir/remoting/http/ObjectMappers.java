/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.http;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;

public final class ObjectMappers {

    static final ObjectMapper VANILLA_MAPPER = vanilla();
    static final ObjectMapper GUAVA_JDK7_MAPPER = guavaJdk7();

    private ObjectMappers() {}

    public static ObjectMapper vanilla() {
        return new ObjectMapper();
    }

    public static ObjectMapper guavaJdk7() {
        // TODO: Replace this code with shading to support different versions of Jackson
        ObjectMapper mapper = new ObjectMapper().registerModule(new GuavaModule());
        try {
            // Newer versions of Jackson no longer ship this module.
            Class<?> jdk7Module = Class.forName("com.fasterxml.jackson.datatype.jdk7.Jdk7Module");
            Module module = (Module) jdk7Module.newInstance();
            mapper.registerModule(module);
        } catch (ReflectiveOperationException e) {
            // We're using a recent version of Jackson
        }

        return mapper;
    }

}
