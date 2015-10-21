/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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
