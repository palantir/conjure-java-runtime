/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.remoting.http.QueryMapTestServer.TestService;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class QueryMapUsingServerWithoutContextTests {

    /**
     * Creates a {@link QueryMapTestServer.WithoutContext} Dropwizard server.
     * The Java implementation of this server uses strongly typed methods that
     * will only process the query parameters that are declared as part of the
     * method parameters.
     */
    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(
            QueryMapTestServer.WithoutContext.class,
            "src/test/resources/test-server.yml");

    private QueryMapTestServer.TestClientService service;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();

        service = FeignClients.standard("test suite user agent").createProxy(
                Optional.<SSLSocketFactory>absent(),
                endpointUri,
                QueryMapTestServer.TestClientService.class);
    }

    @Test
    public void testGetUsingParamMapWithQueryMap() {
        Map<String, String> queryMap = ImmutableMap.of(
                TestService.MAP_PARAM_NAME, "namedVal 1",
                "fooParam", "fooVal");

        // client GET call contains all of the query parameters in queryMap,
        // but Java server implementation is such that only first value of
        // TestService.MAP_PARAM_NAME query parameter is processed
        assertThat(
                service.getUsingParamMap(queryMap),
                is((Map<String, String>) ImmutableMap.of(TestService.MAP_PARAM_NAME, "namedVal 1")));
    }

    @Test
    public void testGetUsingParamMapWithString() {
        assertThat(
                service.getUsingParamMap("fooVal"),
                is((Map<String, String>) ImmutableMap.of(TestService.MAP_PARAM_NAME, "fooVal")));
    }

    @Test
    public void testGetUsingParamListWithQueryMap() {
        Map<String, List<String>> queryMap = ImmutableMap.<String, List<String>>of(
                TestService.LIST_PARAM_NAME, ImmutableList.of("list val 1", "list val 2"),
                "fooParam", ImmutableList.of("foo value"),
                TestService.MAP_PARAM_NAME, ImmutableList.of("bar value"));

        // client GET call contains all of the query parameters in queryMap,
        // but Java server implementation is such that only values of
        // TestService.LIST_PARAM_NAME query parameter are processed
        assertThat(
                service.getUsingParamList(queryMap),
                is((Map<String, List<String>>) ImmutableMap.<String, List<String>>of(
                        TestService.LIST_PARAM_NAME, ImmutableList.of("list val 1", "list val 2"))));
    }

    @Test
    public void testGetUsingParamListWithList() {
        assertThat(
                service.getUsingParamList(ImmutableList.of("foo", "bar")),
                is((Map<String, List<String>>) ImmutableMap.<String, List<String>>of(
                        TestService.LIST_PARAM_NAME, ImmutableList.of("foo", "bar"))));
    }

}
