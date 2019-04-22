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
import com.google.common.collect.Sets;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.tracing.Tracer;
import com.palantir.tracing.api.Span;
import com.palantir.tracing.api.SpanType;
import com.palantir.tracing.api.TraceHttpHeaders;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
    }

    @Test
    public void testClientIsInstrumentedWithTracer() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("\"server\""));

        String parentSpanId = Tracer.startSpan("").getSpanId();
        String traceId = Tracer.getTraceId();

        List<String> spanIds = Lists.newArrayList();
        List<Span> spans = Lists.newArrayList();
        Tracer.subscribe(TracerTest.class.getName(), span -> {
            spanIds.add(span.getSpanId());
            spans.add(span);
        });

        service.param("somevalue");

        Tracer.unsubscribe(TracerTest.class.getName());

        assertThat(spans).containsExactly(
                span(spans.get(0), traceId, spanIds.get(2), SpanType.LOCAL, "OkHttp: limiter-enqueue"),
                span(spans.get(1), traceId, spanIds.get(2), SpanType.LOCAL, "OkHttp: limiter-run"),
                span(spans.get(2), traceId, parentSpanId, SpanType.LOCAL, "OkHttp: enqueue"),
                span(spans.get(3), traceId, spanIds.get(4), SpanType.CLIENT_OUTGOING, "OkHttp: GET /{param}"),
                span(spans.get(4), traceId, spanIds.get(2), SpanType.LOCAL, "OkHttp: dispatcher"));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader(TraceHttpHeaders.TRACE_ID)).isEqualTo(traceId);
        assertThat(request.getHeader(TraceHttpHeaders.SPAN_ID)).isNotEqualTo(parentSpanId);
    }

    @Test
    public void testLimiterAcquisitionMultiThread() {
        reduceConcurrencyLimitTo1();
        Set<String> observedTraceIds = Sets.newConcurrentHashSet();
        addTraceSubscriber(observedTraceIds);
        runTwoRequestsInParallel();
        removeTraceSubscriber();
        assertThat(observedTraceIds).hasSize(2);
    }

    private static Span span(
            Span actualSpan,
            String traceId,
            String parentSpanId,
            SpanType spanType,
            String operation) {
        return Span.builder()
                .from(actualSpan)
                .traceId(traceId)
                .parentSpanId(parentSpanId)
                .type(spanType)
                .operation(operation)
                .build();
    }

    private void runTwoRequestsInParallel() {
        // time based delays are sad, but meh.
        server.enqueue(new MockResponse().setBody("\"server\"").setHeadersDelay(100, TimeUnit.MILLISECONDS));
        server.enqueue(new MockResponse().setBody("\"server\""));

        CompletableFuture<?> first = CompletableFuture.runAsync(() -> {
            Tracer.initTrace(Optional.of(true), "first");
            Tracer.startSpan("");
            service.string();
        });
        CompletableFuture<?> second = CompletableFuture.runAsync(() -> {
            Tracer.initTrace(Optional.of(true), "second");
            Tracer.startSpan("");
            service.string();
        });
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
