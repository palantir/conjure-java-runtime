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

package com.palantir.remoting;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.base.Optional;
import com.palantir.remoting.http.ClientSupplier;
import com.palantir.remoting.http.FeignClientFactory;
import com.palantir.remoting.http.FeignClients;
import com.palantir.remoting.http.GuavaOptionalAwareContract;
import com.palantir.remoting.http.NeverRetryingBackoffStrategy;
import com.palantir.remoting.http.ObjectMappers;
import com.palantir.remoting.http.SlashEncodingContract;
import com.palantir.remoting.http.errors.FeignSerializableErrorErrorDecoder;
import feign.Client;
import feign.InputStreamDelegateDecoder;
import feign.InputStreamDelegateEncoder;
import feign.OptionalAwareDecoder;
import feign.Request;
import feign.TextDelegateDecoder;
import feign.TextDelegateEncoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JaxRsWithHeaderAndQueryMapContract;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

// Verifies that Feign can be used simultaneously with OkHttp 2.x and 3.x.
// TODO(rfink) Remove this when OkHttpClient gets removed.
public final class TestOkhttpFeignCompatibility {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private String endpointUri;

    @Before
    public void before() {
        endpointUri = "http://localhost:" + server.getPort();
        server.enqueue(new MockResponse().setBody("\"foo\""));
    }

    @Test
    public void testOkHttp3_usingStandardFeignClientsFactory() throws Exception {
        TestService service = FeignClients.standard("agent").createProxy(
                Optional.<SSLSocketFactory>absent(),
                endpointUri,
                TestService.class);
        assertThat(service.get(), is("foo"));
    }

    @Test
    public void testOkHttp2_usingCustomClientSupplier() throws Exception {
        FeignClientFactory okHttp2Factory = FeignClientFactory.of(
                new SlashEncodingContract(new GuavaOptionalAwareContract(new JaxRsWithHeaderAndQueryMapContract())),
                new InputStreamDelegateEncoder(new TextDelegateEncoder(new JacksonEncoder(ObjectMappers.guavaJdk7()))),
                new OptionalAwareDecoder(new InputStreamDelegateDecoder(
                        new TextDelegateDecoder(new JacksonDecoder(ObjectMappers.guavaJdk7())))),
                FeignSerializableErrorErrorDecoder.INSTANCE,
                new ClientSupplier() {
                    @Override
                    public Client createClient(Optional<SSLSocketFactory> sslSocketFactory, String userAgent) {
                        return new feign.okhttp.OkHttpClient();
                    }
                },
                NeverRetryingBackoffStrategy.INSTANCE,
                new Request.Options(),
                "agent");
        TestService service = okHttp2Factory.createProxy(
                Optional.<SSLSocketFactory>absent(),
                endpointUri,
                TestService.class);
        assertThat(service.get(), is("foo"));
    }

    @Path("/")
    public interface TestService {
        @GET
        String get();
    }
}
