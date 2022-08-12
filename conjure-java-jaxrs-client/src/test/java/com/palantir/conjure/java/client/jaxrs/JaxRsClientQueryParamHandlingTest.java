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

package com.palantir.conjure.java.client.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.client.jaxrs.ExtensionsWrapper.BeforeAndAfter;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.util.Arrays;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class JaxRsClientQueryParamHandlingTest extends TestBase {

    @RegisterExtension
    public final BeforeAndAfter<MockWebServer> serverResource = ExtensionsWrapper.toExtension(new MockWebServer());

    MockWebServer server;

    private Service proxy;

    @BeforeEach
    public void before() {
        this.server = serverResource.getResource();
        proxy = JaxRsClient.create(
                Service.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig("http://localhost:" + server.getPort()));
        MockResponse mockResponse = new MockResponse().setResponseCode(200);
        server.enqueue(mockResponse);
    }

    @Path("/")
    public interface Service {
        @GET
        @Path("/queryList")
        void queryList(@QueryParam("req") List<String> req);
    }

    @Test
    public void queryListEncodedAsQueryParamsWithSameName() throws Exception {
        proxy.queryList(Arrays.asList("str1", "str2"));
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /queryList?req=str1&req=str2 HTTP/1.1");
    }
}
