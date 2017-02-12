/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting1.servers.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.jersey.DropwizardResourceConfig;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import org.glassfish.jersey.internal.util.Base64;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

public final class BinaryMessageProviderTest extends JerseyTest {

    private static final String STRING = "bytes";
    private static final byte[] BYTES = STRING.getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENCODED_BYTES = jsonEncode(BYTES);

    @Override
    protected Application configure() {
        forceSet(TestProperties.CONTAINER_PORT, "0");
        return DropwizardResourceConfig.forTesting(new MetricRegistry())
                .register(HttpRemotingJerseyFeature.DEFAULT)
                .register(BinaryReturnResource.class);
    }

    @Test
    public void testBinaryWriter() throws Exception {
        byte[] actual = target("/binary-return")
                .request()
                .buildPost(Entity.json(STRING))
                .invoke(byte[].class);
        assertThat(actual).isEqualTo(ENCODED_BYTES);
    }

    @Test
    public void testBinaryReader() throws Exception {
        String actual = target("/binary-consume")
                .request()
                .buildPost(Entity.json(ENCODED_BYTES))
                .invoke(String.class);
        assertThat(actual).isEqualTo(STRING);
    }

    /**
     * JSON requires strings to be wrapped in quotes. The binary wire format is a base64 encoded string, so we must wrap
     * the base64 encoded bytes in quotes.
     */
    private static byte[] jsonEncode(byte[] bytes) {
        byte[] base64Bytes = Base64.encode(bytes);
        String wrapped = "\"" + new String(base64Bytes, StandardCharsets.UTF_8) + "\"";
        return wrapped.getBytes(StandardCharsets.UTF_8);
    }

    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public static final class BinaryReturnResource {
        @POST
        @Path("binary-return")
        public byte[] showWithQueryParam(String string) {
            return string.getBytes(StandardCharsets.UTF_8);
        }

        @POST
        @Path("binary-consume")
        public String byteArrayToString(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
