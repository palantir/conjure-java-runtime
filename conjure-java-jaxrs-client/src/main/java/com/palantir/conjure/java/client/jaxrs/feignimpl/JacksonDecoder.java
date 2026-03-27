/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.conjure.java.client.jaxrs.feignimpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.conjure.java.client.jaxrs.feign.Response;
import com.palantir.conjure.java.client.jaxrs.feign.Util;
import com.palantir.conjure.java.client.jaxrs.feign.codec.Decoder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

public final class JacksonDecoder implements Decoder {

    private final ObjectMapper mapper;

    public JacksonDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        if (response.status() == 404) {
            return Util.emptyValueOf(type);
        }
        if (response.body() == null) {
            return null;
        }
        Reader reader = response.body().asReader();
        if (!reader.markSupported()) {
            reader = new BufferedReader(reader, 1);
        }

        // Read the first byte to see if we have any data
        reader.mark(1);
        if (reader.read() == -1) {
            return null; // Eagerly returning null avoids "No content to map due to end-of-input"
        }
        reader.reset();
        return mapper.readValue(reader, mapper.constructType(type));
    }
}
