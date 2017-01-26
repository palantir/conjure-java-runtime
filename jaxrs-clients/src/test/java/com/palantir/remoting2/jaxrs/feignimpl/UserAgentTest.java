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

package com.palantir.remoting2.jaxrs.feignimpl;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.palantir.remoting2.jaxrs.JaxRsClient;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class UserAgentTest {

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private String endpointUri;

    private static final String USER_AGENT = "TestSuite/1 (0.0.0)";

    @Before
    public void before() {
        endpointUri = "http://localhost:" + server.getPort();

        server.enqueue(new MockResponse().setBody("{}"));
    }

    @Test
    public void testUserAgent_default() throws InterruptedException {
        TestService service = JaxRsClient.builder().build(TestService.class, USER_AGENT, endpointUri);
        service.get();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("User-Agent"), is(USER_AGENT));
    }

    @Test
    public void testUserAgent_invalidUserAgentThrows() throws InterruptedException {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(is("User Agent must match pattern '[A-Za-z0-9()\\-#;/.,_\\s]+': !@"));
        JaxRsClient.builder().build(TestService.class, "!@", endpointUri);
    }

    @Path("/")
    public interface TestService {
        @GET
        void get();
    }
}
