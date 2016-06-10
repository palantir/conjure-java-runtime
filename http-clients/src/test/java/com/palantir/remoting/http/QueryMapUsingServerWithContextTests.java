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

public final class QueryMapUsingServerWithContextTests {

    /**
     * Creates a {@link QueryMapTestServer.WithContext} Dropwizard server.
     * The Java implementation of this server uses the context to read all
     * query parameters from a map and returns them.
     */
    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(
            QueryMapTestServer.WithContext.class,
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
                "fooParam", "fooVal",
                "barParam", "barVal");

        assertThat(service.getUsingParamMap(queryMap), is(queryMap));
    }

    @Test
    public void testGetUsingParamMapWithString() {
        assertThat(service.getUsingParamMap("fooVal"),
                is((Map<String, String>) ImmutableMap.of(TestService.MAP_PARAM_NAME, "fooVal")));
    }

    @Test
    public void testGetUsingParamListWithQueryMap() {
        Map<String, List<String>> queryMap = ImmutableMap.<String, List<String>>of(
                "fooParam", ImmutableList.of("fooVal", "foo value 2", ""),
                "barParam", ImmutableList.of("barVal"));

        assertThat(service.getUsingParamList(queryMap), is(queryMap));
    }

    @Test
    public void testGetUsingParamListWithList() {
        List<String> paramList = ImmutableList.of("foo", "bar");

        assertThat(
                service.getUsingParamList(paramList),
                is((Map<String, List<String>>) ImmutableMap.of(
                        TestService.LIST_PARAM_NAME, paramList)));
    }

}
