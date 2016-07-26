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

package com.palantir.remoting.jaxrs;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.palantir.remoting.clients.ClientConfig;
import com.palantir.remoting.http.BackoffStrategy;
import feign.RetryableException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;


public final class JaxRsClientFailoverTest {

    @Rule
    public final MockWebServer server1 = new MockWebServer();
    @Rule
    public final MockWebServer server2 = new MockWebServer();

    @Test
    public void testFailover() throws Exception {
        server1.shutdown();
        server2.enqueue(new MockResponse().setBody("\"foo\""));

        FakeoInterface proxy = JaxRsClient.builder().build(FakeoInterface.class, "agent",
                ImmutableList.of(
                        "http://localhost:" + server1.getPort(),
                        "http://localhost:" + server2.getPort()));
        assertThat(proxy.blah(), is("foo"));
    }

    @Test
    public void testConsecutiveCalls() throws Exception {
        FakeoInterface proxy = JaxRsClient.builder().build(FakeoInterface.class, "agent",
                ImmutableList.of(
                        "http://localhost:" + server1.getPort(),
                        "http://localhost:" + server2.getPort()));

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

        FakeoInterface proxy = new FeignJaxRsClientBuilder(ClientConfig.builder().build(), backoffStrategy)
                .build(FakeoInterface.class, "agent",
                        ImmutableList.of("http://localhost:" + server1.getPort(),
                                "http://localhost:" + server2.getPort()));
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

    @Path("/fakeo")
    public interface FakeoInterface {
        @GET
        String blah();
    }
}
