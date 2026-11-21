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

package com.palantir.conjure.java.server.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.tracing.Tracer;
import com.palantir.tracing.api.TraceHttpHeaders;
import com.palantir.undertest.UndertowServerExtension;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class TracerTest {

    @RegisterExtension
    public static final UndertowServerExtension undertow = UndertowServerExtension.create()
            .jersey(ConjureJerseyFeature.INSTANCE)
            .jersey(new TracerTest.TracingTestResource());

    @Test
    public void testTracingFilterIsApplied() {
        undertow.runRequest(
                ClassicRequestBuilder.get("/trace")
                        .addHeader(TraceHttpHeaders.TRACE_ID, "traceId")
                        .addHeader(TraceHttpHeaders.PARENT_SPAN_ID, "parentSpanId")
                        .addHeader(TraceHttpHeaders.SPAN_ID, "spanId")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(Status.OK.getStatusCode());
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("traceId");
                    assertThat(response.getFirstHeader(TraceHttpHeaders.TRACE_ID)
                                    .getValue())
                            .isEqualTo("traceId");
                    assertThat(response.getFirstHeader(TraceHttpHeaders.SPAN_ID))
                            .isNull();
                    assertThat(response.getFirstHeader(TraceHttpHeaders.PARENT_SPAN_ID))
                            .isNull();
                });
    }

    public static final class TracingTestResource implements TracingTestService {
        @Override
        public String getTraceOperation() {
            return Tracer.getTraceId();
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TracingTestService {
        @GET
        @Path("/trace")
        String getTraceOperation();
    }
}
