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

package com.palantir.remoting1.jaxrs;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.palantir.remoting1.tracing.TraceState;
import com.palantir.remoting1.tracing.Traces;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class TracingTest {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private TestService service;

    @Before
    public void before() {
        String uri = "http://localhost:" + server.getPort();
        service = JaxRsClient.builder().build(TestService.class, "agent", uri);
        server.enqueue(new MockResponse().setBody("\"server\""));
    }

    @Test
    public void testClientIsInstrumentedWithTracer() throws InterruptedException, IOException {
        TraceState parentTrace = Traces.startSpan("");
        service.echo("foo");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader(Traces.HttpHeaders.TRACE_ID), is(parentTrace.getTraceId()));
        assertThat(request.getHeader(Traces.HttpHeaders.SPAN_ID), is(not(parentTrace.getSpanId())));
    }
}
