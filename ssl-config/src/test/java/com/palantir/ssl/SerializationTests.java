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

package com.palantir.ssl;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.palantir.remoting.ssl.KeyStoreConfiguration;
import com.palantir.remoting.ssl.TrustStoreConfiguration;
import java.io.IOException;
import java.net.URI;
import org.junit.Test;

public final class SerializationTests {

    @Test
    public void testJsonSerDe() throws JsonParseException, JsonMappingException, JsonProcessingException, IOException {
        ObjectMapper mapper = new ObjectMapper().registerModule(new GuavaModule());
        KeyStoreConfiguration keystore = KeyStoreConfiguration.of(URI.create("/path"), "password");
        TrustStoreConfiguration truststore = TrustStoreConfiguration.of(URI.create("/path"));

        assertThat(mapper.readValue(mapper.writeValueAsBytes(keystore), KeyStoreConfiguration.class), is(keystore));
        assertThat(mapper.readValue(mapper.writeValueAsBytes(truststore), TrustStoreConfiguration.class),
                is(truststore));
    }
}
