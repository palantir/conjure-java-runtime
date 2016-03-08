/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import feign.Contract;
import feign.MethodMetadata;
import feign.QueryMap;
import feign.RequestLine;
import feign.jaxrs.JAXRSContract;
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

public final class QueryMapAwareContractTests {

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Test
    public void testMethodWithQueryMapAndBody() {
        // Contract.Default knows how to interpret QueryMap annotation
        QueryMapAwareContract contract = new QueryMapAwareContract(new Contract.Default());
        List<MethodMetadata> mdl = contract.parseAndValidatateMetadata(QueryMapTestInterfaceWithBodyFeign.class);

        assertThat(mdl.size(), is(1));
        assertThat(Iterables.getOnlyElement(mdl).queryMapIndex(), is(0));
    }

    @Test
    public void testMethodWithQueryMapAndBodyFails() {
        // JAXRSContract does not know how to interpret QueryMap annotation; assumes it is a body parameter
        QueryMapAwareContract contract = new QueryMapAwareContract(new JAXRSContract());

        try {
            contract.parseAndValidatateMetadata(QueryMapTestInterfaceWithBodyJaxRs.class);
            fail();
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage(), containsString("Method has too many Body parameters"));
        }
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
