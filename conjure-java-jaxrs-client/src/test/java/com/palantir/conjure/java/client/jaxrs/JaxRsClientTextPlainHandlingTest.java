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
import static org.assertj.core.api.Assertions.assertThatCode;

import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import feign.codec.DecodeException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class JaxRsClientTextPlainHandlingTest extends TestBase {
    @Rule
    public final MockWebServer server = new MockWebServer();

    private Service proxy;

    @Before
    public void before() {
        proxy = JaxRsClient.create(Service.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig("http://localhost:" + server.getPort()));
    }

    @Path("/")
    public interface Service {
        @GET
        String textString();

        @GET
        Object textObject();

        @GET
        Integer textInteger();
    }

    @Test
    public void testEmptyTextPlainString() {
        server.enqueue(new MockResponse()
                .setResponseCode(Status.NO_CONTENT.getStatusCode())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN));
        assertThat(proxy.textString()).isNull();
    }

    @Test
    public void testEmptyTextPlainObject() {
        server.enqueue(new MockResponse()
                .setResponseCode(Status.NO_CONTENT.getStatusCode())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN));
        assertThat(proxy.textObject()).isNull();
    }

    @Test
    public void testEmptyTextPlainInteger() {
        server.enqueue(new MockResponse()
                .setResponseCode(Status.NO_CONTENT.getStatusCode())
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN));
        assertThatCode(() -> proxy.textInteger()).isInstanceOf(DecodeException.class)
                .hasMessageContaining("unable to construct an empty instance for return type");
    }
}
