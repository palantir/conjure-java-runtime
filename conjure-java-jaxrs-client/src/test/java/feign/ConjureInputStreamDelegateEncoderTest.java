/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;

import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.client.jaxrs.feignimpl.GuavaTestServer;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import feign.codec.Encoder;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class ConjureInputStreamDelegateEncoderTest extends TestBase {
    @Mock
    private Encoder delegate;

    private final RequestTemplate requestTemplate = new RequestTemplate();

    private Encoder inputStreamDelegateEncoder;

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(GuavaTestServer.class,
            "src/test/resources/test-server.yml");

    private GuavaTestServer.TestService service;

    @Before
    public void before() {
        inputStreamDelegateEncoder = new ConjureInputStreamDelegateEncoder(delegate);

        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = JaxRsClient.create(
                GuavaTestServer.TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig(endpointUri));
    }

    @Test
    public void testEncodesAsInputStream() throws Exception {
        byte[] object = bytes("data");

        inputStreamDelegateEncoder.encode(new ByteArrayInputStream(object), InputStream.class, requestTemplate);
        assertThat(requestTemplate.body(), is(object));
    }

    @Test
    public void testUsesDelegateWithNonInputStreamBodyType() throws Exception {
        String data = "data";

        inputStreamDelegateEncoder.encode(data, String.class, requestTemplate);
        verify(delegate).encode(data, String.class, requestTemplate);
    }

    @Test
    public void testStandardClientsUseByteArrayDelegateEncoder() {
        String data = "bytes";
        assertThat(service.readInputStream(new ByteArrayInputStream(bytes(data))), is(data));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
