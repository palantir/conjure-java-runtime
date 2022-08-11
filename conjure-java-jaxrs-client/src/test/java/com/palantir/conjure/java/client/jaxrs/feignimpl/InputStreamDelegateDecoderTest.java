/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.undertest.UndertowServerExtension;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

public final class InputStreamDelegateDecoderTest extends TestBase {

    @RegisterExtension
    public static final UndertowServerExtension undertow = GuavaTestServer.createUndertow();

    private GuavaTestServer.TestService service;
    private Decoder delegate;
    private Decoder inputStreamDelegateDecoder;

    @BeforeEach
    public void before() {
        delegate = Mockito.mock(Decoder.class);
        inputStreamDelegateDecoder = new InputStreamDelegateDecoder(delegate);

        String endpointUri = "http://localhost:" + undertow.getLocalPort();
        service = JaxRsClient.create(
                GuavaTestServer.TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(endpointUri));
    }

    @Test
    public void testDecodesAsInputStream() throws Exception {
        String data = "data";

        Response response = Response.create(200, "OK", ImmutableMap.of(), data, StandardCharsets.UTF_8);

        InputStream decoded = (InputStream) inputStreamDelegateDecoder.decode(response, InputStream.class);

        assertThat(new String(Util.toByteArray(decoded), StandardCharsets.UTF_8))
                .isEqualTo(data);
    }

    @Test
    public void testUsesDelegateWhenReturnTypeNotInputStream() throws Exception {
        String returned = "string";

        when(delegate.decode(any(), any())).thenReturn(returned);
        Response response = Response.create(200, "OK", ImmutableMap.of(), returned, StandardCharsets.UTF_8);
        String decodedObject = (String) inputStreamDelegateDecoder.decode(response, String.class);
        assertThat(decodedObject).isEqualTo(returned);
    }

    @Test
    public void testSupportsNullBody() throws Exception {
        String data = "";
        Response response = Response.create(200, "OK", ImmutableMap.of(), (Response.Body) null);

        InputStream decoded = (InputStream) inputStreamDelegateDecoder.decode(response, InputStream.class);

        assertThat(new String(Util.toByteArray(decoded), StandardCharsets.UTF_8))
                .isEqualTo(data);
    }

    @Test
    public void testStandardClientsUseInputStreamDelegateDecoder() throws IOException {
        String data = "bytes";
        assertThat(Util.toByteArray(service.writeInputStream(data))).isEqualTo(bytes(data));
    }

    @Test
    public void testClientCanHandleEmptyInputStream() throws IOException {
        String data = "";
        assertThat(Util.toByteArray(service.writeInputStream(data))).isEqualTo(bytes(data));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
