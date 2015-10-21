/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import org.junit.Rule;
import org.junit.Test;


public final class FailoverFeignTargetTest {

    @Rule
    public final MockWebServer server1 = new MockWebServer();
    @Rule
    public final MockWebServer server2 = new MockWebServer();

    @Test
    public void testFailover() throws Exception {
        server1.shutdown();
        server2.enqueue(new MockResponse().setBody("\"foo\""));

        FakeoInterface proxy = FeignClients.standard()
                .createProxy(
                    Optional.<SSLSocketFactory>absent(),
                    ImmutableSet.of("http://localhost:" + server1.getPort(), "http://localhost:" + server2.getPort()),
                    FakeoInterface.class);
        assertThat(proxy.blah(), is("foo"));
    }

    @Path("/fakeo")
    public interface FakeoInterface {
        @GET
        String blah();
    }
}
