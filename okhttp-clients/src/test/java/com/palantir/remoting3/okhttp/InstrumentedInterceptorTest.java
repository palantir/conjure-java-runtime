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

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Map;
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
    public void testResponseFamilyMetrics() throws IOException {
        Map<Integer, String> testCases = ImmutableMap.<Integer, String>builder()
                .put(100, "informational")
                .put(200, "successful")
                .put(300, "redirection")
                .put(400, "client-error")
                .put(500, "server-error")
                .put(600, "other")
                .build();

        for (Map.Entry<Integer, String> testCase : testCases.entrySet()) {
            Meter meter = registry.getMeters().get("client.response.family." + testCase.getValue());
            assertThat(meter.getCount()).isZero();

            responseWithCode(testCase.getKey());
            interceptor.intercept(chain);

            assertThat(meter.getCount()).isEqualTo(1);
        }
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
