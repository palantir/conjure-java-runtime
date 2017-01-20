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

package feign;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.palantir.remoting1.jaxrs.JaxRsClient;
import com.palantir.remoting1.jaxrs.feignimpl.GoogleTestServer;
import feign.codec.Decoder;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;

public final class InputStreamDelegateDecoderTest {
    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(GoogleTestServer.class,
            "src/test/resources/test-server.yml");

    private GoogleTestServer.TestService service;
    private Decoder delegate;
    private Decoder inputStreamDelegateDecoder;

    @Before
    public void before() {
        delegate = Mockito.mock(Decoder.class);
        inputStreamDelegateDecoder = new InputStreamDelegateDecoder(delegate);

        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = JaxRsClient.builder()
                .build(GoogleTestServer.TestService.class, "agent", endpointUri);
    }

    @Test
    public void testDecodesAsInputStream() throws Exception {
        String data = "data";

        Response response =
                Response.create(200, "OK", ImmutableMap.<String, Collection<String>>of(), data, StandardCharsets.UTF_8);

        InputStream decoded = (InputStream) inputStreamDelegateDecoder.decode(response, InputStream.class);

        assertThat(new String(Util.toByteArray(decoded), StandardCharsets.UTF_8), is(data));
    }

    @Test
    public void testUsesDelegateWhenReturnTypeNotInputStream() throws Exception {
        String returned = "string";

        when(delegate.decode((Response) any(), (Type) any())).thenReturn(returned);
        Response response =
                Response.create(200, "OK", ImmutableMap.<String, Collection<String>>of(), returned,
                        StandardCharsets.UTF_8);
        String decodedObject = (String) inputStreamDelegateDecoder.decode(response, String.class);
        assertEquals(returned, decodedObject);
    }

    @Test
    public void testStandardClientsUseInputStreamDelegateDecoder() throws IOException {
        String data = "bytes";
        assertThat(Util.toByteArray(service.writeInputStream(data)), is(bytes(data)));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
