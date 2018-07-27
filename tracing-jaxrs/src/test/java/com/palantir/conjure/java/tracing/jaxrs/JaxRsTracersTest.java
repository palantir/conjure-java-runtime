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

package com.palantir.conjure.java.tracing.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.tracing.Tracer;
import java.io.ByteArrayOutputStream;
import javax.ws.rs.core.StreamingOutput;
import org.junit.Test;

public final class JaxRsTracersTest {

    @Test
    public void testWrappingStreamingOutput_streamingOutputTraceIsIsolated() throws Exception {
        Tracer.startSpan("outside");
        StreamingOutput streamingOutput = JaxRsTracers.wrap((os) -> {
            Tracer.startSpan("inside"); // never completed
        });
        streamingOutput.write(new ByteArrayOutputStream());
        assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("outside");
    }

    @Test
    public void testWrappingStreamingOutput_traceStateIsCapturedAtConstructionTime() throws Exception {
        Tracer.startSpan("before-construction");
        StreamingOutput streamingOutput = JaxRsTracers.wrap((os) -> {
            assertThat(Tracer.completeSpan().get().getOperation()).isEqualTo("before-construction");
        });
        Tracer.startSpan("after-construction");
        streamingOutput.write(new ByteArrayOutputStream());
    }
}
