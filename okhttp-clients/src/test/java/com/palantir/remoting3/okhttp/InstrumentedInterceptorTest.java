/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.codahale.metrics.MetricRegistry;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class InstrumentedInterceptorTest {

    private static final Request REQUEST = new Request.Builder().url("http://url").build();

    @Mock
    private Interceptor.Chain chain;

    private InstrumentedInterceptor interceptor;
    private MetricRegistry registry;

    @Before
    public void before() throws IOException {
        when(chain.request()).thenReturn(REQUEST);

        registry = new MetricRegistry();
        interceptor = new InstrumentedInterceptor(registry, "client");
    }

    @Test
    public void testInformational() throws IOException {
        responseWithCode(100);
        interceptor.intercept(chain);

        assertThat(registry.getMeters().get("client.response.code.1xx").getCount()).isEqualTo(1);
    }

    @Test
    public void testSuccessful() throws IOException {
        responseWithCode(200);
        interceptor.intercept(chain);

        assertThat(registry.getMeters().get("client.response.code.2xx").getCount()).isEqualTo(1);
    }

    @Test
    public void testRedirect() throws IOException {
        responseWithCode(300);
        interceptor.intercept(chain);

        assertThat(registry.getMeters().get("client.response.code.3xx").getCount()).isEqualTo(1);
    }

    @Test
    public void testClientError() throws IOException {
        responseWithCode(400);
        interceptor.intercept(chain);

        assertThat(registry.getMeters().get("client.response.code.4xx").getCount()).isEqualTo(1);
    }

    @Test
    public void testServerError() throws IOException {
        responseWithCode(500);
        interceptor.intercept(chain);

        assertThat(registry.getMeters().get("client.response.code.5xx").getCount()).isEqualTo(1);
    }

    private void responseWithCode(int code) throws IOException {
        Response response = new Response.Builder()
                .request(REQUEST)
                .message("")
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .build();
        when(chain.proceed(REQUEST)).thenReturn(response);
    }
}
