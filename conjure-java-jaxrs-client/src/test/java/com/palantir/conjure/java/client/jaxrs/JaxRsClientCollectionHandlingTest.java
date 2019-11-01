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

package com.palantir.conjure.java.client.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class JaxRsClientCollectionHandlingTest extends TestBase {

    @Rule public final MockWebServer server = new MockWebServer();

    private Service proxy;

    @Parameters(name = "{index}: code {0} body: \"{1}\"")
    public static Collection<Object[]> responses() {
        return Arrays.asList(new Object[][] {
            {200, "null"},
            {200, ""},
            {204, ""}
        });
    }

    @Parameter public int code;

    @Parameter(1)
    public String body;

    @Before
    public void before() {
        proxy = JaxRsClient.create(Service.class, AGENT, new HostMetricsRegistry(), createTestConfig("http://localhost:"
                + server.getPort()));
        MockResponse mockResponse = new MockResponse().setResponseCode(code).setBody(body);
        server.enqueue(mockResponse);
    }

    @Path("/")
    public interface Service {
        @GET
        @Path("/list")
        List<String> getList();

        @GET
        @Path("/set")
        Set<String> getSet();

        @GET
        @Path("/map")
        Map<String, String> getMap();
    }

    @Test
    public void testList() {
        assertThat(proxy.getList()).isEmpty();
    }

    @Test
    public void testSet() {
        assertThat(proxy.getSet()).isEmpty();
    }

    @Test
    public void testMap() {
        assertThat(proxy.getMap()).isEmpty();
    }
}
