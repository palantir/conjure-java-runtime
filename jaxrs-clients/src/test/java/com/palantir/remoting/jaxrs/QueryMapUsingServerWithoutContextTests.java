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
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.remoting.jaxrs.QueryMapTestServer.TestService;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class QueryMapUsingServerWithoutContextTests {

    /**
     * Creates a {@link com.palantir.remoting.jaxrs.QueryMapTestServer.WithoutContext} Dropwizard server.
     * The Java implementation of this server uses strongly typed methods that
     * will only process the query parameters that are declared as part of the
     * method parameters.
     */
    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(
            com.palantir.remoting.jaxrs.QueryMapTestServer.WithoutContext.class,
            "src/test/resources/test-server.yml");

    private com.palantir.remoting.jaxrs.QueryMapTestServer.TestClientService service;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = JaxRsClient.builder().build(QueryMapTestServer.TestClientService.class, "agent", endpointUri);
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
