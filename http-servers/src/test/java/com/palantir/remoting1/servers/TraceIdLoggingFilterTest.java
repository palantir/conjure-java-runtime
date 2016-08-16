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

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.kristofa.brave.http.BraveHttpHeaders;
import java.io.IOException;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

public final class TraceIdLoggingFilterTest {

    private static final String TRACE_ID = "myTraceId";

    @Before
    public void setUp() throws Exception {
        MDC.clear();
    }

    @After
    public void tearDown() throws Exception {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        if (contextMap != null) {
            assertThat(contextMap.entrySet(), empty());
        }
    }

    @Test
    public void testFilter_setsMdcIfTraceIdHeaderIsPresent() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(BraveHttpHeaders.TraceId.getName())).thenReturn(TRACE_ID);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response)
                    throws IOException, ServletException {
                assertThat(MDC.get(TraceIdLoggingFilter.MDC_KEY), is(TRACE_ID));
            }
        };

        TraceIdLoggingFilter.INSTANCE.doFilter(request, response, chain);
        assertThat(MDC.get(TraceIdLoggingFilter.MDC_KEY), is(nullValue()));
    }


    @Test
    public void testFilter_setsMdcIfTraceIdHeaderIsNotPresent() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(BraveHttpHeaders.TraceId.getName())).thenReturn(null);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response)
                    throws IOException, ServletException {
                assertThat(MDC.get(TraceIdLoggingFilter.MDC_KEY), is(nullValue()));
            }
        };

        TraceIdLoggingFilter.INSTANCE.doFilter(request, response, chain);
        assertThat(MDC.get(TraceIdLoggingFilter.MDC_KEY), is(nullValue()));
    }

    @Test
    public void testFilter_clearsMdc() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(BraveHttpHeaders.TraceId.getName())).thenReturn(TRACE_ID);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response)
                    throws IOException, ServletException {
                assertThat(MDC.get(TraceIdLoggingFilter.MDC_KEY), is("myTraceId"));
                throw new IOException("expected");
            }
        };

        try {
            TraceIdLoggingFilter.INSTANCE.doFilter(request, response, chain);
            fail("Expected exception");
        } catch (IOException expected) {
            assertThat(expected.getMessage(), is("expected"));
        } finally {
            assertThat(MDC.get(TraceIdLoggingFilter.MDC_KEY), is(nullValue()));
        }
    }
}
