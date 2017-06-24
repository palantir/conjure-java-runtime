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

package com.palantir.remoting2.config.ssl;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.remoting.api.config.ssl.SslConfiguration;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import java.io.IOException;
import org.junit.Test;

public final class SerializationTests {

    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();

    @Test
    public void testJsonSerDe() throws IOException {

        SslConfiguration sslConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);

        assertThat(MAPPER.readValue(MAPPER.writeValueAsBytes(sslConfig), SslConfiguration.class), is(sslConfig));
    }

    @Test
    public void testJsonDeserialize() throws IOException {
        SslConfiguration sslConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);

        assertThat(MAPPER.readValue(JSON_STRING, SslConfiguration.class), is(sslConfig));
    }

    private static final String JSON_STRING =
            "{"
                    + "\"trustStorePath\":\"src/test/resources/testCA/testCA.jks\","
                    + "\"trustStoreType\":\"JKS\","
                    + "\"keyStorePath\":\"src/test/resources/testServer/testServer.jks\","
                    + "\"keyStorePassword\":\"serverStore\","
                    + "\"keyStoreType\":\"JKS\","
                    + "\"keyStoreKeyAlias\":null"
                    + "}";

}
