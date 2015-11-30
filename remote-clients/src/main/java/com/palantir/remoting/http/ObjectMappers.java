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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk7.Jdk7Module;

public final class ObjectMappers {

    private static final ObjectMapper VANILLA_MAPPER = new ObjectMapper();

    private static final ObjectMapper GUAVA_JDK7_MAPPER = new ObjectMapper()
            .registerModule(new GuavaModule())
            .registerModule(new Jdk7Module());

    private ObjectMappers() {}

    public static ObjectMapper vanilla() {
        return VANILLA_MAPPER;
    }

    public static ObjectMapper guavaJdk7() {
        return GUAVA_JDK7_MAPPER;
    }

}
