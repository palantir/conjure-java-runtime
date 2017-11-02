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
import com.palantir.remoting3.okhttp.metrics.HostMetrics;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
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

    private static final Request REQUEST_A = new Request.Builder().url("http://hostA").build();
    private static final Request REQUEST_B = new Request.Builder().url("http://hostB").build();

    @Mock
    private Interceptor.Chain chain;

    private InstrumentedInterceptor interceptor;
    private TaggedMetricRegistry registry;

    @Before
    public void before() throws IOException {
        registry = new DefaultTaggedMetricRegistry();
        interceptor = new InstrumentedInterceptor(registry, "client");
    }

    private static MetricName name(String hostname) {
        return MetricName.builder()
                .safeName("client" + HostMetrics.CLIENT_RESPONSE_METRIC_NAME_SUFFIX)
                .putSafeTags(HostMetrics.HOSTNAME_TAG, hostname)
                .putSafeTags(HostMetrics.FAMILY_TAG, "successful")
                .build();
    }

    @Test
    public void testResponseFamilyMetrics() throws IOException {
        successfulRequest(REQUEST_A);
        interceptor.intercept(chain);

        Meter meterA = registry.meter(name("hosta"));
        assertThat(meterA.getCount()).isEqualTo(1);

        successfulRequest(REQUEST_B);
        interceptor.intercept(chain);

        Meter meterB = registry.meter(name("hostb"));
        assertThat(meterB.getCount()).isEqualTo(1);
    }

    private void successfulRequest(Request request) throws IOException {
        Response response = new Response.Builder()
                .request(request)
                .message("")
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .build();
        when(chain.request()).thenReturn(request);
        when(chain.proceed(request)).thenReturn(response);
    }
}
