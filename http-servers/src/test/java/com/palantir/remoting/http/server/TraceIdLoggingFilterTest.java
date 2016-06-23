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

package com.palantir.remoting.http.server;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.kristofa.brave.http.BraveHttpHeaders;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.slf4j.MDC;

public final class TraceIdLoggingFilterTest {

    @Test
    public void testFilter_setsMdcIfTraceIdHeaderIsPresent() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(BraveHttpHeaders.TraceId.getName())).thenReturn("myTraceId");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        TraceIdLoggingFilter.INSTANCE.doFilter(request, response, chain);
        assertThat(MDC.get(TraceIdLoggingFilter.MDC_KEY), is("myTraceId"));
    }
}
