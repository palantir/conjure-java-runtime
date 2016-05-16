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

package com.palantir.remoting.retrofit;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import com.google.common.base.Optional;
import com.palantir.tracing.TraceState;
import com.palantir.tracing.Traces;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import java.util.UUID;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import retrofit.http.GET;

public final class TracingRequestInterceptorTest {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private TestRequestInterceptorService service;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + server.getPort();

        service = RetrofitClientFactory.createProxy(
                Optional.<SSLSocketFactory>absent(),
                endpointUri,
                TestRequestInterceptorService.class,
                OkHttpClientOptions.builder().build());

        server.enqueue(new MockResponse().setBody("\"ok\""));
    }

    @Test
    public void testTraceRequestInterceptor_sendsAValidTraceId() throws InterruptedException {
        service.get();
        RecordedRequest request = server.takeRequest();

        String traceId = request.getHeader(Traces.Headers.TRACE_ID);
        assertThat(UUID.fromString(traceId).toString(), is(traceId));
    }

    @Test
    public void testTraceRequestInterceptor_sendsExplicitTraceId() throws InterruptedException {
        TraceState state = Traces.deriveTrace("operation");
        service.get();
        RecordedRequest request = server.takeRequest();

        assertThat(request.getHeader(Traces.Headers.TRACE_ID), is(state.getTraceId()));
        assertThat(request.getHeader(Traces.Headers.PARENT_SPAN_ID), is(state.getSpanId()));
        assertThat(request.getHeader(Traces.Headers.SPAN_ID), not(state.getSpanId()));
    }

    public interface TestRequestInterceptorService {
        @GET("/")
        String get();
    }

}
