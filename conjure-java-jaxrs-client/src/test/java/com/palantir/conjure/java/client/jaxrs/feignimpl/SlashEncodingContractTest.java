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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.palantir.conjure.java.client.jaxrs.ExtensionsWrapper;
import com.palantir.conjure.java.client.jaxrs.ExtensionsWrapper.BeforeAndAfter;
import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.undertest.UndertowServerExtension;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class SlashEncodingContractTest extends TestBase {
    private static final Service resource = mock(Service.class);

    @RegisterExtension
    public static final UndertowServerExtension undertow =
            UndertowServerExtension.create().jersey(resource);

    private static final String PATH_PARAM = "slash/path";
    private static final String QUERY_PARAM = "slash/query";

    @RegisterExtension
    public final BeforeAndAfter<MockWebServer> serverResource = ExtensionsWrapper.toExtension(new MockWebServer());

    MockWebServer server;

    private Service jerseyProxy;
    private Service inMemoryProxy;

    @BeforeEach
    public void before() {
        this.server = serverResource.getResource();
        jerseyProxy = JaxRsClient.create(
                Service.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig("http://localhost:" + undertow.getLocalPort()));
        inMemoryProxy = JaxRsClient.create(
                Service.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig("http://localhost:" + server.getPort()));
        server.enqueue(new MockResponse().setBody("\"foo\""));
    }

    @Test
    public void testJerseyDeocodesPathAndQueryParams() {
        when(resource.encoded(any(), any())).thenReturn("Hello, world");
        jerseyProxy.encoded(PATH_PARAM, QUERY_PARAM);
        verify(resource).encoded(PATH_PARAM, QUERY_PARAM);
    }

    @Test
    public void testPathAndQueryParamsAreEncoded() throws InterruptedException {
        inMemoryProxy.encoded(PATH_PARAM, QUERY_PARAM);
        RecordedRequest request = server.takeRequest();
        // See https://www.ietf.org/rfc/rfc3986.txt section 3.4: Query
        // The characters slash ("/") and question mark ("?") may represent data within the query component.
        assertThat(request.getPath()).isEqualTo("/path/slash%2Fpath?query=slash/query");
    }

    @Path("/")
    public interface Service {
        @GET
        @Path("path/{path}")
        String encoded(@PathParam("path") String path, @QueryParam("query") String query);
    }
}
