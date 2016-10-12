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

package com.palantir.remoting.retrofit;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.base.Optional;
import com.palantir.remoting1.retrofit.OkHttpClientOptions;
import com.palantir.remoting1.retrofit.RetrofitClientFactory;
import com.palantir.remoting1.servers.jersey.TraceEnrichingFilter;
import com.palantir.remoting1.tracing.TraceState;
import com.palantir.remoting1.tracing.Traces;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class TraceRequestResponseTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(TracingTestServer.class,
            "src/test/resources/test-server.yml");

    private RetrofitTracingTestService service;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();

        service = RetrofitClientFactory.createProxy(
                Optional.<SSLSocketFactory>absent(),
                endpointUri,
                RetrofitTracingTestService.class,
                OkHttpClientOptions.builder().build());
    }

    @Test
    public void testTraceResponseDecoder_decoderPopsMatchingSpan() {
        Optional<TraceState> before = Traces.getTrace();
        service.get();

        assertThat(Traces.getTrace(), is(before));
    }

    public static class TracingTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(new TraceEnrichingFilter());
            env.jersey().register(new TracingTestResource());
        }
    }

    public static final class TracingTestResource implements TracingTestService {
        @Override
        public Object get() {
            return "{}";
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TracingTestService {
        @GET
        @Path("/trace")
        Object get();
    }

    public interface RetrofitTracingTestService {
        @retrofit.http.GET("/trace")
        Object get();
    }
}
