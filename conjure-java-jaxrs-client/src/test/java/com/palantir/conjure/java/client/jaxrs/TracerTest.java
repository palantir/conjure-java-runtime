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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.tracing.CloseableTracer;
import com.palantir.tracing.Observability;
import com.palantir.tracing.RenderTracingRule;
import com.palantir.tracing.Tracer;
import com.palantir.tracing.Tracers;
import com.palantir.tracing.api.OpenSpan;
import com.palantir.tracing.api.SpanType;
import com.palantir.tracing.api.TraceHttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.Assertions;
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
        service = JaxRsClient.create(TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(uri));
    }

    @Test
    public void testClientIsInstrumentedWithTracer() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("\"server\""));
        Tracer.initTrace(Observability.SAMPLE, Tracers.randomId());
        OpenSpan parentTrace = Tracer.startSpan("");
        List<Map.Entry<SpanType, String>> observedSpans = Lists.newArrayList();
        Tracer.subscribe(TracerTest.class.getName(),
                span -> observedSpans.add(Maps.immutableEntry(span.type(), span.getOperation())));

        String traceId = Tracer.getTraceId();
        service.param("somevalue");

        Tracer.unsubscribe(TracerTest.class.getName());
        assertThat(observedSpans).containsExactlyInAnyOrder(
                Maps.immutableEntry(SpanType.LOCAL, "OkHttp: GET /{param}"),
                Maps.immutableEntry(SpanType.LOCAL, "OkHttp: attempt 0"),
                Maps.immutableEntry(SpanType.LOCAL, "OkHttp: client-side-concurrency-limiter 0/10"),
                Maps.immutableEntry(SpanType.LOCAL, "OkHttp: dispatcher"),
                Maps.immutableEntry(SpanType.CLIENT_OUTGOING, "OkHttp: wait-for-headers"),
                Maps.immutableEntry(SpanType.CLIENT_OUTGOING, "OkHttp: wait-for-body"));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader(TraceHttpHeaders.TRACE_ID)).isEqualTo(traceId);
        assertThat(request.getHeader(TraceHttpHeaders.SPAN_ID)).isNotEqualTo(parentTrace.getSpanId());
    }

    @Test
    public void test503_eventually_works() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setBody("\"foo\""));
        try (CloseableTracer span = CloseableTracer.startSpan("test-retries")) {
            service.param("somevalue");
        }
    }

    @Test
    public void give_me_some_delays() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeadersDelay(100, TimeUnit.MILLISECONDS)
                .setHeader("Content-Type", "application/json")
                .setBodyDelay(300, TimeUnit.MILLISECONDS)
                .setBody("\"stringy mc stringface\""));
        try (CloseableTracer span = CloseableTracer.startSpan("test")) {
            service.param("somevalue");
        }
    }

    @Test
    public void test503_exhausting_retries() throws InterruptedException {
        // Default is 4 retries, so doing 5
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        try (CloseableTracer span = CloseableTracer.startSpan("test-retries")) {
            Assertions
                    .assertThatCode(() -> service.param("somevalue"))
                    .hasRootCauseExactlyInstanceOf(QosException.Unavailable.class);
        }
    }

    @Test
    public void testLimiterAcquisitionMultiThread() {
        reduceConcurrencyLimitTo1();
        Set<String> observedTraceIds = ConcurrentHashMap.newKeySet();
        addTraceSubscriber(observedTraceIds);
        runTwoRequestsInParallel();
        removeTraceSubscriber();
        assertThat(observedTraceIds).hasSize(2);
    }

    private void runTwoRequestsInParallel() {
        // time based delays are sad, but meh.
        server.enqueue(new MockResponse().setBody("\"server\"").setHeadersDelay(100, TimeUnit.MILLISECONDS));
        server.enqueue(new MockResponse().setBody("\"server\""));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CompletableFuture<?> first = CompletableFuture.runAsync(() -> {
            Tracer.initTrace(Optional.of(true), "first");
            OpenSpan ignored = Tracer.startSpan("");
            service.string();
        }, executor);
        CompletableFuture<?> second = CompletableFuture.runAsync(() -> {
            Tracer.initTrace(Optional.of(true), "second");
            OpenSpan ignored = Tracer.startSpan("");
            service.string();
        }, executor);
        first.join();
        second.join();
    }

    private void addTraceSubscriber(Set<String> observedTraceIds) {
        Tracer.subscribe(TracerTest.class.getName(), span -> {
            if (span.getOperation().equals("OkHttp: GET /string")) {
                observedTraceIds.add(span.getTraceId());
            }
        });
    }

    private void removeTraceSubscriber() {
        Tracer.unsubscribe(TracerTest.class.getName());
    }

    private void reduceConcurrencyLimitTo1() {
        int experimentallyThisNeedsToHappen3Times = 3;
        for (int i = 0; i < experimentallyThisNeedsToHappen3Times; i++) {
            IntStream.range(0, 3).forEach(x -> {
                server.enqueue(new MockResponse().setResponseCode(429));
            });
            server.enqueue(new MockResponse().setBody("\"server\""));
            assertThat(service.string()).isEqualTo("server");
        }
    }
}
