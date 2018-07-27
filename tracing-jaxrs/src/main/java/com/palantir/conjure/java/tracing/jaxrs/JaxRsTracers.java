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

import com.palantir.conjure.java.tracing.DeferredTracer;
import com.palantir.conjure.java.tracing.Tracers;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

public final class JaxRsTracers {

    private JaxRsTracers() {}

    /** Like {@link Tracers#wrap(Callable)}, but for StreamingOutputs. */
    public static StreamingOutput wrap(StreamingOutput delegate) {
        return new TracingAwareStreamingOutput(delegate);
    }

    private static class TracingAwareStreamingOutput implements StreamingOutput {

        private final StreamingOutput delegate;
        private DeferredTracer deferredTracer;

        TracingAwareStreamingOutput(StreamingOutput delegate) {
            this.delegate = delegate;
            this.deferredTracer = new DeferredTracer();
        }

        @Override
        public void write(OutputStream output) throws IOException, WebApplicationException {
            deferredTracer.withTrace(() -> {
                delegate.write(output);
                return null;
            });
        }
    }
}
