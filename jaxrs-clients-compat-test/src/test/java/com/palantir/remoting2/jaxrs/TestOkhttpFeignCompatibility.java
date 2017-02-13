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

package com.palantir.remoting2.jaxrs;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import feign.Feign;
import feign.jaxrs.JAXRSContract;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

// Verifies that Feign can be used simultaneously with OkHttp 2.x and 3.x.
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
    public void testOkHttp3_usingStandardJaxRsClient() throws Exception {
        TestService service = JaxRsClient.builder().build(TestService.class, "agent", endpointUri);
        assertThat(service.get(), is("foo"));
    }

    @Test
    public void testOkHttp2_withVanillaFeign() throws Exception {
        TestService service = Feign.builder()
                .contract(new JAXRSContract())
                .target(TestService.class, endpointUri);
        assertThat(service.get(), is("\"foo\""));
    }

    @Path("/")
    public interface TestService {
        @GET
        String get();
    }
}
