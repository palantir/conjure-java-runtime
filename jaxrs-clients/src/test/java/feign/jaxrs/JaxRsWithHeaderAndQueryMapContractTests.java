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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.palantir.remoting.jaxrs.JaxRsClient;
import feign.Contract;
import feign.HeaderMap;
import feign.MethodMetadata;
import feign.QueryMap;
import feign.RequestLine;
import java.util.List;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.Test;

public final class JaxRsWithHeaderAndQueryMapContractTests {

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Test
    public void testMethodWithHeaderMapAndBodyDefaultContract() {
        // Contract.Default knows how to interpret HeaderMap annotation
        Contract contract = new Contract.Default();
        List<MethodMetadata> mdl = contract.parseAndValidatateMetadata(HeaderMapTestInterfaceWithBodyFeign.class);

        assertThat(mdl.size(), is(1));
        assertThat(Iterables.getOnlyElement(mdl).headerMapIndex(), is(0));
    }

    @Test
    public void testMethodWithHeaderMapAndBodyJaxRsContractFails() {
        // JAXRSContract does not know how to interpret HeaderMap annotation; assumes it is a body parameter
        Contract contract = new JAXRSContract();

        try {
            contract.parseAndValidatateMetadata(QueryMapTestInterfaceWithBodyJaxRs.class);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), containsString("Method has too many Body parameters"));
        }
    }

    @Test
    public void testMethodWithHeaderMapAndBodyJaxRsWithHeaderAndQueryMapContract() {
        JaxRsWithHeaderAndQueryMapContract contract = new JaxRsWithHeaderAndQueryMapContract();

        List<MethodMetadata> mdl = contract.parseAndValidatateMetadata(HeaderMapTestInterfaceWithBodyJaxRs.class);

        assertThat(mdl.size(), is(1));
        assertThat(Iterables.getOnlyElement(mdl).headerMapIndex(), is(0));
    }

    @Test
    public void testJaxRsInterfaceWithHeaderMap() throws Exception {
        HeaderMapTestInterface proxy = JaxRsClient.builder()
                .build(HeaderMapTestInterface.class, "agent", "http://localhost:" + server.getPort());
        server.enqueue(new MockResponse());

        proxy.header("alice", ImmutableMap.of("fooKey", "fooValue"));

        RecordedRequest recordedRequest = server.takeRequest();
        assertThat(recordedRequest.getHeader("name"), is("alice"));
        assertThat(recordedRequest.getHeader("fooKey"), is("fooValue"));
    }

    @Test
    public void testMethodWithQueryMapAndBodyDefaultContract() {
        // Contract.Default knows how to interpret QueryMap annotation
        Contract contract = new Contract.Default();
        List<MethodMetadata> mdl = contract.parseAndValidatateMetadata(QueryMapTestInterfaceWithBodyFeign.class);

        assertThat(mdl.size(), is(1));
        assertThat(Iterables.getOnlyElement(mdl).queryMapIndex(), is(0));
    }

    @Test
    public void testMethodWithQueryMapAndBodyJaxRsContractFails() {
        // JAXRSContract does not know how to interpret HeaderMap annotation; assumes it is a body parameter
        Contract contract = new JAXRSContract();

        try {
            contract.parseAndValidatateMetadata(QueryMapTestInterfaceWithBodyJaxRs.class);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), containsString("Method has too many Body parameters"));
        }
    }

    @Test
    public void testMethodWithQueryMapAndBodyJaxRsWithHeaderAndQueryMapContract() {
        JaxRsWithHeaderAndQueryMapContract contract = new JaxRsWithHeaderAndQueryMapContract();

        List<MethodMetadata> mdl = contract.parseAndValidatateMetadata(QueryMapTestInterfaceWithBodyJaxRs.class);

        assertThat(mdl.size(), is(1));
        assertThat(Iterables.getOnlyElement(mdl).queryMapIndex(), is(0));
    }

    @Test
    public void testJaxRsInterfaceWithQueryMap() throws Exception {
        QueryMapTestInterface proxy = JaxRsClient.builder()
                .build(QueryMapTestInterface.class, "agent", "http://localhost:" + server.getPort());
        server.enqueue(new MockResponse());

        proxy.query("alice", ImmutableMap.of("fooKey", "fooValue"));

        assertThat(server.takeRequest().getPath(), is("/query?name=alice&fooKey=fooValue"));
    }

    @Path("/")
    private interface HeaderMapTestInterface {
        @GET
        @Path("header")
        String header(@HeaderParam("name") String name, @HeaderMap Map<String, String> headerMap);
    }

    @Path("/")
    private interface HeaderMapTestInterfaceWithBodyJaxRs {
        @GET
        @Path("header")
        String header(@HeaderMap Map<String, String> headerMap, String body);
    }

    private interface HeaderMapTestInterfaceWithBodyFeign {
        @PUT
        @RequestLine("PUT /bar")
        String header(@HeaderMap Map<String, String> headerMap, String body);
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
