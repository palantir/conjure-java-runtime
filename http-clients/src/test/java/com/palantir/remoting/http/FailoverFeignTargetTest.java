/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.http;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting.http.errors.SerializableErrorErrorDecoder;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import feign.Request;
import feign.RetryableException;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JAXRSContract;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;


public final class FailoverFeignTargetTest {

    @Rule
    public final MockWebServer server1 = new MockWebServer();
    @Rule
    public final MockWebServer server2 = new MockWebServer();

    @Test
    public void testFailover() throws Exception {
        server1.shutdown();
        server2.enqueue(new MockResponse().setBody("\"foo\""));

        FakeoInterface proxy = FeignClients.standard().createProxy(Optional.<SSLSocketFactory>absent(),
                ImmutableSet.of("http://localhost:" + server1.getPort(), "http://localhost:" + server2.getPort()),
                FakeoInterface.class);
        assertThat(proxy.blah(), is("foo"));
    }

    @Test
    public void testConsecutiveCalls() throws Exception {
        FakeoInterface proxy = FeignClients.standard().createProxy(Optional.<SSLSocketFactory>absent(),
                ImmutableSet.of("http://localhost:" + server1.getPort(), "http://localhost:" + server2.getPort()),
                FakeoInterface.class);

        // Call fails when servers are down.
        server1.shutdown();
        server2.shutdown();
        try {
            proxy.blah();
            fail();
        } catch (RetryableException e) {
            assertThat(e.getMessage(), startsWith("Failed to connect"));
        }

        // Subsequent call (with the same proxy instance) succeeds since Feign clones the retryer.
        MockWebServer anotherServer1 = new MockWebServer(); // Not a @Rule so we can control start/stop/port explicitly
        anotherServer1.start(server1.getPort());
        anotherServer1.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(proxy.blah(), is("foo"));
        anotherServer1.shutdown();
    }

    @Test
    public void testBackoff() throws Exception {
        server1.shutdown();
        server2.shutdown();

        BackoffStrategy backoffStrategy = mock(BackoffStrategy.class);
        when(backoffStrategy.backoff(anyInt())).thenReturn(true, false, true, false);

        FakeoInterface proxy = buildProxy(backoffStrategy, NeverRetryingBackoffStrategy.INSTANCE);
        try {
            proxy.blah();
            fail();
        } catch (RetryableException e) {
            assertThat(e.getMessage(), startsWith("Failed to connect"));
        }

        InOrder inOrder = inOrder(backoffStrategy);
        inOrder.verify(backoffStrategy).backoff(1); // server 1, attempt 1
        inOrder.verify(backoffStrategy).backoff(2); // server 1, attempt 2
        inOrder.verify(backoffStrategy).backoff(1); // server 2, attempt 1
        inOrder.verify(backoffStrategy).backoff(2); // server 2, attempt 2
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLeaderElection() throws Exception {
        BackoffStrategy backoffStrategy = mock(BackoffStrategy.class);
        when(backoffStrategy.backoff(anyInt())).thenReturn(true, false, true, false);
        BackoffStrategy leaderElectionBackoffStrategy = mock(BackoffStrategy.class);
        when(leaderElectionBackoffStrategy.backoff(anyInt())).thenReturn(true, true, true, false);

        FakeoInterface proxy = buildProxy(backoffStrategy, leaderElectionBackoffStrategy);

        server1.enqueue(getMockFollowerResponse());
        server2.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(proxy.blah(), is("foo"));
    }

    @Test
    public void testLeaderElectionWithRetry() throws Exception {
        BackoffStrategy leaderElectionBackoffStrategy = mock(BackoffStrategy.class);
        when(leaderElectionBackoffStrategy.backoff(anyInt())).thenReturn(true);

        FakeoInterface proxy = buildProxy(NeverRetryingBackoffStrategy.INSTANCE, leaderElectionBackoffStrategy);

        server1.enqueue(getMockFollowerResponse());
        server2.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(proxy.blah(), is("foo"));

        server1.enqueue(getMockFollowerResponse());
        server1.enqueue(getMockFollowerResponse());
        server1.enqueue(new MockResponse().setBody("\"foo2\""));
        server2.shutdown();
        assertThat(proxy.blah(), is("foo2"));

        InOrder inOrder = inOrder(leaderElectionBackoffStrategy);
        inOrder.verify(leaderElectionBackoffStrategy).backoff(1); // attempt 1
        inOrder.verify(leaderElectionBackoffStrategy).backoff(2); // attempt 2
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testFailedLeaderElection() throws Exception {
        FakeoInterface proxy = buildProxy(NeverRetryingBackoffStrategy.INSTANCE, NeverRetryingBackoffStrategy.INSTANCE);

        server1.enqueue(getMockFollowerResponse());
        server2.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(proxy.blah(), is("foo"));

        server1.enqueue(getMockFollowerResponse());
        server1.enqueue(getMockFollowerResponse());
        server1.enqueue(new MockResponse().setBody("\"foo2\""));
        server2.shutdown();
        try {
            proxy.blah();
            fail();
        } catch (RetryableException e) {
            assertThat(e.getMessage(), startsWith("not leader"));
        }
    }

    @Path("/fakeo")
    public interface FakeoInterface {
        @GET
        String blah();
    }

    private static MockResponse getMockFollowerResponse() {
        return new MockResponse().setResponseCode(503)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .setBody("{'message':'not leader','exceptionClass':'com.palantir.remoting.http.NotLeaderException'}"
                        .replace("'", "\""));
    }

    private FakeoInterface buildProxy(BackoffStrategy backoffStrategy, BackoffStrategy leaderElectionBackoffStrategy) {
        return FeignClientFactory.of(
                new JAXRSContract(),
                new JacksonEncoder(ObjectMappers.guavaJdk7()),
                new JacksonDecoder(ObjectMappers.guavaJdk7()),
                SerializableErrorErrorDecoder.INSTANCE,
                FeignClientFactory.okHttpClient(),
                backoffStrategy,
                leaderElectionBackoffStrategy,
                new Request.Options())
                .createProxy(Optional.<SSLSocketFactory>absent(),
                        ImmutableSet.of("http://localhost:" + server1.getPort(),
                                "http://localhost:" + server2.getPort()),
                        FakeoInterface.class);
    }
}
