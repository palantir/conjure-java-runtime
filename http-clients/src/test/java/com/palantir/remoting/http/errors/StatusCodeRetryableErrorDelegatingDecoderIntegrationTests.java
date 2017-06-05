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

package com.palantir.remoting.http.errors;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.palantir.remoting.http.FeignClientFactory;
import com.palantir.remoting.http.GuavaOptionalAwareContract;
import com.palantir.remoting.http.NeverRetryingBackoffStrategy;
import com.palantir.remoting.http.ObjectMappers;
import com.palantir.remoting.http.SlashEncodingContract;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import feign.InputStreamDelegateDecoder;
import feign.InputStreamDelegateEncoder;
import feign.OptionalAwareDecoder;
import feign.Request;
import feign.TextDelegateDecoder;
import feign.TextDelegateEncoder;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JaxRsWithHeaderAndQueryMapContract;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.junit.Rule;
import org.junit.Test;

public final class StatusCodeRetryableErrorDelegatingDecoderIntegrationTests {

    @Rule
    public final MockWebServer server1 = new MockWebServer();
    @Rule
    public final MockWebServer server2 = new MockWebServer();

    @Test
    public void testFailoverOccursOnSpecifiedErrorCode() throws Exception {
        int notFoundStatus = Response.Status.NOT_FOUND.getStatusCode();

        server1.enqueue(new MockResponse().setResponseCode(notFoundStatus).setBody("\"fail\""));
        server2.enqueue(new MockResponse().setBody("\"success\""));

        ErrorDecoder decoder = new StatusCodeRetryableErrorDelegatingDecoder(
                ImmutableSet.of(notFoundStatus),
                FeignSerializableErrorErrorDecoder.INSTANCE);

        FakeoInterface proxy = createFactory(decoder).createProxy(Optional.<SSLSocketFactory>absent(),
                ImmutableSet.of("http://localhost:" + server1.getPort(), "http://localhost:" + server2.getPort()),
                FakeoInterface.class);

        assertThat(proxy.blah(), is("success"));
    }

    private FeignClientFactory createFactory(ErrorDecoder decoder) {
        return FeignClientFactory.of(
                new SlashEncodingContract(new GuavaOptionalAwareContract(new JaxRsWithHeaderAndQueryMapContract())),
                new InputStreamDelegateEncoder(new TextDelegateEncoder(new JacksonEncoder(ObjectMappers.guavaJdk7()))),
                new OptionalAwareDecoder(new InputStreamDelegateDecoder(
                        new TextDelegateDecoder(new JacksonDecoder(ObjectMappers.guavaJdk7())))),
                decoder,
                FeignClientFactory.okHttpClient(),
                NeverRetryingBackoffStrategy.INSTANCE,
                new Request.Options());
    }

    @Path("/fakeo")
    public interface FakeoInterface {
        @GET
        String blah();
    }
}
