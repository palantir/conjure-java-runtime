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

package com.palantir.remoting.http;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.base.Optional;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class TextEncoderTest {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private TextEncoderService service;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + server.getPort();

        service = FeignClients.standard().createProxy(
                Optional.<SSLSocketFactory>absent(),
                endpointUri,
                TextEncoderService.class);

        server.enqueue(new MockResponse().setBody("{}"));
    }

    @Test
    public void testTextEncoder_doesNotEscapeAsJson() throws InterruptedException {
        String testString = "{\"key\": \"value\"}";
        service.post(testString);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getBody().readUtf8(), is(testString));
    }

    @Path("/")
    public interface TextEncoderService {
        @POST
        @Consumes(MediaType.TEXT_PLAIN)
        Object post(String test);
    }

}
