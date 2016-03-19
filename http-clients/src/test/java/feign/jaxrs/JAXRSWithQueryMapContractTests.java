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

package feign.jaxrs;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.palantir.remoting.http.FeignClients;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import feign.Contract;
import feign.MethodMetadata;
import feign.QueryMap;
import feign.RequestLine;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import jersey.repackaged.com.google.common.collect.Iterables;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings("checkstyle:abbreviationaswordinname")
public final class JAXRSWithQueryMapContractTests {

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Test
    public void testMethodWithQueryMapAndBodyDefaultContract() {
        // Contract.Default knows how to interpret QueryMap annotation
        Contract contract = new Contract.Default();
        List<MethodMetadata> mdl = contract.parseAndValidatateMetadata(QueryMapTestInterfaceWithBodyFeign.class);

        assertThat(mdl.size(), is(1));
        assertThat(Iterables.getOnlyElement(mdl).queryMapIndex(), is(0));
    }

    @Test
    public void testMethodWithQueryMapAndBodyJAXRSWithQueryMapContract() {
        // JAXRSContract does not know how to interpret QueryMap annotation; assumes it is a body parameter
        JAXRSWithQueryMapContract contract = new JAXRSWithQueryMapContract();

        List<MethodMetadata> mdl = contract.parseAndValidatateMetadata(QueryMapTestInterfaceWithBodyJaxRs.class);

        assertThat(mdl.size(), is(1));
        assertThat(Iterables.getOnlyElement(mdl).queryMapIndex(), is(0));
    }

    @Test
    public void testJaxRsInterfaceWithQueryMap() throws Exception {
        QueryMapTestInterface proxy = FeignClients.standard().createProxy(Optional.<SSLSocketFactory>absent(),
                ImmutableSet.of("http://localhost:" + server.getPort()),
                QueryMapTestInterface.class);
        server.enqueue(new MockResponse());

        proxy.query("alice", ImmutableMap.of("fooKey", "fooValue"));

        assertThat(server.takeRequest().getPath(), is("/query?name=alice&fooKey=fooValue"));
    }

    @Path("/")
    private interface QueryMapTestInterface {
        @GET
        @Path("query")
        String query(@QueryParam("name") String name, @QueryMap Map<String, String> queryMap);
    }

    @Path("/")
    private interface QueryMapTestInterfaceWithBodyJaxRs {
        @PUT
        @Path("bar")
        void queryWithBody(@QueryMap Map<String, String> queryMap, String body);
    }

    private interface QueryMapTestInterfaceWithBodyFeign {
        @PUT
        @RequestLine("PUT /bar")
        void queryWithBody(@QueryMap Map<String, String> queryMap, String body);
    }
}
