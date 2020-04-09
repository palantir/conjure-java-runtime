/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
import java.io.ByteArrayInputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.AbstractMap;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public final class JaxRsClientDialogueEndpointTest {

    @Test
    public void testEndpoint() {
        ConjureRuntime runtime = DefaultConjureRuntime.builder().build();
        Channel channel = mock(Channel.class);
        Response response = mock(Response.class);
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(response.code()).thenReturn(204);
        when(response.headers()).thenReturn(ImmutableListMultimap.of());
        when(channel.execute(any(Endpoint.class), any(Request.class))).thenReturn(Futures.immediateFuture(response));
        StubService service = JaxRsClient.create(StubService.class, channel, runtime);
        service.ping();

        ArgumentCaptor<Endpoint> endpointCaptor = ArgumentCaptor.forClass(Endpoint.class);
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(channel).execute(endpointCaptor.capture(), requestCaptor.capture());
        Endpoint endpoint = endpointCaptor.getValue();
        assertThat(endpoint.serviceName()).isEqualTo("StubService");
        assertThat(endpoint.endpointName()).isEqualTo("ping");
        assertThat(endpoint.httpMethod()).isEqualTo(HttpMethod.GET);
        Request request = requestCaptor.getValue();
        assertThat(request.body()).isEmpty();
        assertThat(request.headerParams().asMap())
                .containsExactly(
                        new AbstractMap.SimpleImmutableEntry<>("Accept", ImmutableList.of("application/json")));
    }

    @Test
    public void testQueryParameterCollection() {
        ConjureRuntime runtime = DefaultConjureRuntime.builder().build();
        Channel channel = mock(Channel.class);
        Response response = mock(Response.class);
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(response.code()).thenReturn(204);
        when(response.headers()).thenReturn(ImmutableListMultimap.of());
        when(channel.execute(any(Endpoint.class), any(Request.class))).thenReturn(Futures.immediateFuture(response));
        StubService service = JaxRsClient.create(StubService.class, channel, runtime);
        service.collectionOfQueryParams(ImmutableList.of("a", "/", "", "a b", "a+b"));

        ArgumentCaptor<Endpoint> endpointCaptor = ArgumentCaptor.forClass(Endpoint.class);
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(channel).execute(endpointCaptor.capture(), requestCaptor.capture());
        UrlBuilder urlBuilder = mock(UrlBuilder.class);
        endpointCaptor.getValue().renderPath(ImmutableMap.of(), urlBuilder);
        verify(urlBuilder).queryParam("query", "a");
        verify(urlBuilder).queryParam("query", "/");
        verify(urlBuilder).queryParam("query", "");
        verify(urlBuilder).queryParam("query", "a b");
        verify(urlBuilder).queryParam("query", "a+b");
    }

    @Test
    public void testPostWithBody() {
        ConjureRuntime runtime = DefaultConjureRuntime.builder().build();
        Channel channel = mock(Channel.class);
        Response response = mock(Response.class);
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(response.code()).thenReturn(204);
        when(response.headers()).thenReturn(ImmutableListMultimap.of());
        when(channel.execute(any(Endpoint.class), any(Request.class))).thenReturn(Futures.immediateFuture(response));
        StubService service = JaxRsClient.create(StubService.class, channel, runtime);
        service.post("Hello, World!");

        ArgumentCaptor<Endpoint> endpointCaptor = ArgumentCaptor.forClass(Endpoint.class);
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(channel).execute(endpointCaptor.capture(), requestCaptor.capture());
        Endpoint endpoint = endpointCaptor.getValue();
        assertThat(endpoint.serviceName()).isEqualTo("StubService");
        assertThat(endpoint.endpointName()).isEqualTo("post");
        assertThat(endpoint.httpMethod()).isEqualTo(HttpMethod.POST);
        Request request = requestCaptor.getValue();
        assertThat(request.body()).isPresent();
        assertThat(request.body().get().contentType()).isEqualTo("text/plain");
        assertThat(request.headerParams().asMap())
                .containsExactly(
                        new AbstractMap.SimpleImmutableEntry<>("Accept", ImmutableList.of("application/json")));
    }

    @Test
    public void testPostWithBody_defaultContentType() {
        ConjureRuntime runtime = DefaultConjureRuntime.builder().build();
        Channel channel = mock(Channel.class);
        Response response = mock(Response.class);
        when(response.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(response.code()).thenReturn(204);
        when(response.headers()).thenReturn(ImmutableListMultimap.of());
        when(channel.execute(any(Endpoint.class), any(Request.class))).thenReturn(Futures.immediateFuture(response));
        StubServiceWithoutContentType service =
                JaxRsClient.create(StubServiceWithoutContentType.class, channel, runtime);
        service.post("Hello, World!");

        ArgumentCaptor<Endpoint> endpointCaptor = ArgumentCaptor.forClass(Endpoint.class);
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(channel).execute(endpointCaptor.capture(), requestCaptor.capture());
        Endpoint endpoint = endpointCaptor.getValue();
        assertThat(endpoint.serviceName()).isEqualTo("StubServiceWithoutContentType");
        assertThat(endpoint.endpointName()).isEqualTo("post");
        assertThat(endpoint.httpMethod()).isEqualTo(HttpMethod.POST);
        Request request = requestCaptor.getValue();
        assertThat(request.body()).isPresent();
        assertThat(request.body().get().contentType()).isEqualTo("application/json");
        assertThat(request.headerParams().asMap()).isEmpty();
    }

    @Test
    public void testUnsupportedHttpMethod_options() {
        ConjureRuntime runtime = DefaultConjureRuntime.builder().build();
        Channel channel = mock(Channel.class);
        assertThatThrownBy(() -> JaxRsClient.create(OptionsService.class, channel, runtime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported HTTP method")
                .hasMessageContaining("OPTIONS");
    }

    @Test
    public void testUnsupportedHttpMethod_arbitrary() {
        ConjureRuntime runtime = DefaultConjureRuntime.builder().build();
        Channel channel = mock(Channel.class);
        assertThatThrownBy(() -> JaxRsClient.create(ArbitraryMethodService.class, channel, runtime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported HTTP method")
                .hasMessageContaining("ARBITRARY");
    }

    @Path("foo")
    @Produces("application/json")
    @Consumes("application/json")
    public interface StubService {

        @GET
        @Path("path")
        void ping();

        @GET
        @Path("params")
        void collectionOfQueryParams(@QueryParam("query") List<String> values);

        @POST
        @Path("post")
        @Consumes("text/plain")
        void post(String body);
    }

    @Path("bar")
    public interface StubServiceWithoutContentType {

        @POST
        @Path("post")
        void post(String body);
    }

    @Path("foo")
    @Produces("application/json")
    @Consumes("application/json")
    public interface OptionsService {

        @Path("options")
        @OPTIONS
        void options();
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @javax.ws.rs.HttpMethod("ARBITRARY")
    public @interface ArbitraryHttpMethod {}

    @Path("foo")
    @Produces("application/json")
    @Consumes("application/json")
    public interface ArbitraryMethodService {

        @Path("arbitrary")
        @ArbitraryHttpMethod
        void arbitrary();
    }
}
