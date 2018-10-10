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

import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.tracing.Tracer;
import com.palantir.tracing.api.OpenSpan;
import com.palantir.tracing.api.TraceHttpHeaders;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class TracerTest extends TestBase {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private TestService service;

    @Before
    public void before() {
        String uri = "http://localhost:" + server.getPort();
        service = JaxRsClient.create(TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(uri));
        server.enqueue(new MockResponse().setBody("\"server\""));
    }

    @Test
    public void testClientIsInstrumentedWithTracer() throws InterruptedException, IOException {
        OpenSpan parentTrace = Tracer.startSpan("");

        Tracer.subscribe(TracerTest.class.getName(), (span) -> {
            assertThat(span.getOperation(), either(is("acquireLimiter")).or(is("GET /{param}")));
        });

        String traceId = Tracer.getTraceId();
        service.param("somevalue");

        Tracer.unsubscribe(TracerTest.class.getName());

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader(TraceHttpHeaders.TRACE_ID), is(traceId));
        assertThat(request.getHeader(TraceHttpHeaders.SPAN_ID), is(not(parentTrace.getSpanId())));
    }
}
