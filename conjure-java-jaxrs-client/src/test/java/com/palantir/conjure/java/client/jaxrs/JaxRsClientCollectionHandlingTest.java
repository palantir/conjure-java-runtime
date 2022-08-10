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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public final class JaxRsClientCollectionHandlingTest extends TestBase {

    public MockWebServer server;

    private Service proxy;

    @BeforeEach
    public void before() throws IOException {
        server = new MockWebServer();
        server.start();
        proxy = JaxRsClient.create(
                Service.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig("http://localhost:" + server.getPort()));
    }

    @AfterEach
    public void afterEach() throws IOException {
        server.shutdown();
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

    @ParameterizedTest(name = "{index}: code {0} body: \"{1}\"")
    @ArgumentsSource(Args.class)
    public void testList(int code, String body) {
        MockResponse mockResponse = new MockResponse().setResponseCode(code).setBody(body);
        server.enqueue(mockResponse);

        assertThat(proxy.getList()).isEmpty();
    }

    @ParameterizedTest(name = "{index}: code {0} body: \"{1}\"")
    @ArgumentsSource(Args.class)
    public void testSet(int code, String body) {
        MockResponse mockResponse = new MockResponse().setResponseCode(code).setBody(body);
        server.enqueue(mockResponse);

        assertThat(proxy.getSet()).isEmpty();
    }

    @ParameterizedTest(name = "{index}: code {0} body: \"{1}\"")
    @ArgumentsSource(Args.class)
    public void testMap(int code, String body) {
        MockResponse mockResponse = new MockResponse().setResponseCode(code).setBody(body);
        server.enqueue(mockResponse);

        assertThat(proxy.getMap()).isEmpty();
    }

    private static final class Args implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            return Stream.of(Arguments.of(200, "null"), Arguments.of(200, ""), Arguments.of(204, ""));
        }
    }
}
