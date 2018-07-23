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

package com.palantir.conjure.java.client.retrofit2;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

import com.palantir.conjure.java.clients.UserAgent;
import com.palantir.conjure.java.clients.UserAgents;
import com.palantir.conjure.java.okhttp.OkHttpClients;
import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class Retrofit2ClientBuilderTest extends TestBase {

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testRelativeAndAbsoluteRetrofitEndPoints_workWithArbitraryBaseUrlFormats() throws Exception {
        assertRequestUrlYieldsHttpPath("/api/", "/api/%s");
        assertRequestUrlYieldsHttpPath("/api", "/api/%s");
        assertRequestUrlYieldsHttpPath("api/", "/api/%s");
        assertRequestUrlYieldsHttpPath("api", "/api/%s");
        assertRequestUrlYieldsHttpPath("/", "/%s");
        assertRequestUrlYieldsHttpPath("", "/%s");
    }

    private void assertRequestUrlYieldsHttpPath(String basePath, String expectedQueryPath) throws Exception {
        HttpUrl url = server.url(basePath);
        TestService
                service = Retrofit2Client.create(TestService.class, AGENT, createTestConfig(url.toString()));

        server.enqueue(new MockResponse().setBody("\"server\""));
        assertThat(service.get().execute().body(), is("server"));
        assertThat(server.takeRequest().getPath(), is(String.format(expectedQueryPath, "")));

        server.enqueue(new MockResponse().setBody("\"server\""));
        assertThat(service.getRelative().execute().body(), is("server"));
        assertThat(server.takeRequest().getPath(), is(String.format(expectedQueryPath, "relative")));
    }

    @Test
    public void testUserAgent_defaultHeaderIsSent() throws InterruptedException, IOException {
        TestService service = Retrofit2Client.create(
                TestService.class, AGENT, createTestConfig(
                        String.format("http://%s:%s/api/", server.getHostName().toUpperCase(), server.getPort())));
        server.enqueue(new MockResponse().setBody("\"server\""));
        service.get().execute();

        RecordedRequest capturedRequest = server.takeRequest();
        assertThat(capturedRequest.getHeader("User-Agent"), startsWith(UserAgents.format(AGENT)));
    }

    @Test
    public void testUserAgent_usesUnknownAgentIfBogusAgentIsGiven() throws InterruptedException, IOException {
        TestService service = Retrofit2Client.create(
                TestService.class, "bogus user agent", createTestConfig(
                        String.format("http://%s:%s/api/", server.getHostName().toUpperCase(), server.getPort())));
        server.enqueue(new MockResponse().setBody("\"server\""));
        service.get().execute();

        RecordedRequest capturedRequest = server.takeRequest();
        assertThat(capturedRequest.getHeader("User-Agent"), startsWith("unknown/0.0.0"));
    }

    @Test
    public void testUserAgent_augmentedByHttpRemotingAndServiceComponents() throws Exception {
        TestService service = Retrofit2Client.create(
                TestService.class, AGENT, createTestConfig(
                        String.format("http://%s:%s/api/", server.getHostName().toUpperCase(), server.getPort())));
        server.enqueue(new MockResponse().setBody("\"server\""));
        service.get().execute();

        RecordedRequest request = server.takeRequest();
        String remotingVersion = OkHttpClients.class.getPackage().getImplementationVersion();
        UserAgent expected = AGENT
                .addAgent(UserAgent.Agent.of("TestService", "0.0.0"))
                .addAgent(UserAgent.Agent.of("http-remoting", remotingVersion != null ? remotingVersion : "0.0.0"));
        assertThat(request.getHeader("User-Agent"), is(UserAgents.format(expected)));
    }
}
