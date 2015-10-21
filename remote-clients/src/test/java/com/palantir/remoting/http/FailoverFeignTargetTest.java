/*
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
