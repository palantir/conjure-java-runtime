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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.util.concurrent.Futures;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.io.ByteArrayInputStream;
import java.util.AbstractMap;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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

    @Path("foo")
    @Produces("application/json")
    @Consumes("application/json")
    public interface StubService {

        @GET
        @Path("path")
        void ping();
    }
}
