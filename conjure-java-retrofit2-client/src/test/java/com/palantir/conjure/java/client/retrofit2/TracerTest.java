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
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.google.common.util.concurrent.Futures;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.tracing.RenderTracingRule;
import com.palantir.tracing.Tracer;
import com.palantir.tracing.api.OpenSpan;
import com.palantir.tracing.api.TraceHttpHeaders;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class TracerTest extends TestBase {

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Rule
    public final RenderTracingRule renderTracingRule = new RenderTracingRule();

    private TestService service;

    @Before
    public void before() {
        String uri = "http://localhost:" + server.getPort();
        service = Retrofit2Client.create(TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(uri));
    }

    @Test
    public void testClientIsInstrumentedWithTracer() throws InterruptedException, IOException {
        OpenSpan parentTrace = Tracer.startSpan("");
        String traceId = Tracer.getTraceId();

        server.enqueue(new MockResponse().setBody("\"server\""));
        service.get().execute();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader(TraceHttpHeaders.TRACE_ID), is(traceId));
        assertThat(request.getHeader(TraceHttpHeaders.SPAN_ID), is(not(parentTrace.getSpanId())));
    }

    @Test
    public void makeListenableFutureRequest() throws Exception {
        server.enqueue(new MockResponse().setBodyDelay(3, TimeUnit.SECONDS).setBody("\"stringy mc stringface\""));
        service.makeListenableFutureRequest().get(10, TimeUnit.SECONDS);
    }
}
