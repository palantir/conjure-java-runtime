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

package com.palantir.remoting1.servers;

import com.github.kristofa.brave.http.BraveHttpHeaders;
import java.io.Closeable;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

enum TraceIdLoggingFilter implements Filter {
    INSTANCE;

    /** The key under which trace ids are inserted into SLF4J {@link org.slf4j.MDC MDCs}. */
    static final String MDC_KEY = "traceId";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Closeable traceContext = populateTraceContext(request);
        try {
            chain.doFilter(request, response);
        } finally {
            traceContext.close();
        }
    }

    private Closeable populateTraceContext(ServletRequest request) {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String traceId = httpRequest.getHeader(BraveHttpHeaders.TraceId.getName());
            if (traceId != null) {
                return MDC.putCloseable(MDC_KEY, traceId);
            }
        }
        return NoOpCloseable.INSTANCE;
    }

    @Override
    public void destroy() {}

    private enum NoOpCloseable implements Closeable {
        INSTANCE;

        @Override
        public void close() {
            // no-op
        }
    }

}
