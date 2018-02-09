/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.tracing.jaxrs;

import com.palantir.remoting3.context.DeferredContext;
import com.palantir.remoting3.tracing.Tracers;
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
        private DeferredContext deferredContext;

        TracingAwareStreamingOutput(StreamingOutput delegate) {
            this.delegate = delegate;
            this.deferredContext = new DeferredContext();
        }

        @Override
        public void write(OutputStream output) throws IOException, WebApplicationException {
            deferredContext.withContext(() -> {
                delegate.write(output);
                return null;
            });
        }
    }
}
