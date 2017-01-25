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

package com.palantir.remoting2.jaxrs.feignimpl;

import com.google.common.collect.ImmutableMap;
import feign.QueryMap;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

public final class QueryMapTestServer {

    /**
     * Example server implementation that uses a {@link UriInfo} provided by a
     * {@link Context} annotation to retrieve all of the query parameters for
     * a call on the server side.
     */
    public static final class WithContext extends Application<Configuration> {
        @Override
        public void run(Configuration config, final Environment env) throws Exception {
            // register class so that context is injected on each request
            env.jersey().register(TestResourceWithContext.class);
        }

        public static final class TestResourceWithContext implements TestService {
            @Context
            private UriInfo uriInfo;

            @Override
            public Map<String, String> getUsingParamMap(String foo) {
                Map<String, List<String>> allQueryParamsFromContext = getAllQueryParamsFromContext();

                ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
                for (Entry<String, List<String>> currEntry : allQueryParamsFromContext.entrySet()) {
                    builder.put(currEntry.getKey(), currEntry.getValue().get(0));
                }

                return builder.build();
            }

            @Override
            public Map<String, List<String>> getUsingParamList(List<String> namedParamList) {
                return getAllQueryParamsFromContext();
            }

            private Map<String, List<String>> getAllQueryParamsFromContext() {
                MultivaluedMap<String, String> params = uriInfo.getQueryParameters();

                ImmutableMap.Builder<String, List<String>> builder = ImmutableMap.builder();
                for (Entry<String, List<String>> currEntry : params.entrySet()) {
                    builder.put(currEntry.getKey(), currEntry.getValue());
                }

                return builder.build();
            }
        }
    }

    /**
     * Example server implementation that returns only the query parameters that are
     * explicitly annotated.
     */
    public static final class WithoutContext extends Application<Configuration> {
        @Override
        public void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(new TestResourceWithoutContext());
        }

        public static final class TestResourceWithoutContext implements TestService {
            @Override
            public Map<String, String> getUsingParamMap(String namedParam) {
                return ImmutableMap.of(MAP_PARAM_NAME, namedParam);
            }

            @Override
            public Map<String, List<String>> getUsingParamList(List<String> namedParamList) {
                return ImmutableMap.of(LIST_PARAM_NAME, namedParamList);
            }
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TestService {
        String MAP_PARAM_NAME = "namedParam";
        String MAP_PARAM_PATH = "/getUsingParamMap";

        String LIST_PARAM_NAME = "namedParamList";
        String LIST_PARAM_PATH = "/getUsingParamList";

        @GET
        @Path(MAP_PARAM_PATH)
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Map<String, String> getUsingParamMap(@QueryParam(MAP_PARAM_NAME) String namedParam);

        @GET
        @Path(LIST_PARAM_PATH)
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Map<String, List<String>> getUsingParamList(@QueryParam(LIST_PARAM_NAME) List<String> namedParamList);
    }

    /**
     * Example of a client-specific service interface. Extends {@link TestService}, which is
     * the shared service interface. Provides additional methods that accept a {@link QueryMap}
     * that allow the client to provide arbitrary query parameters (rather than just those that are
     * explicitly defined and annotated in {@link TestService}).
     */
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TestClientService extends TestService {
        @GET
        @Path(MAP_PARAM_PATH)
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Map<String, String> getUsingParamMap(@QueryMap Map<String, String> queryParams);

        @GET
        @Path(LIST_PARAM_PATH)
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Map<String, List<String>> getUsingParamList(@QueryMap Map<String, List<String>> queryParams);
    }

    private QueryMapTestServer() {}
}
