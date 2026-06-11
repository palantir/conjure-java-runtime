/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.server.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.net.HttpHeaders;
import com.palantir.undertest.UndertowServerExtension;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.Status;
import java.util.Optional;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class SmileContentNegotiationTest {

    private static final String SMILE_PREFERRED_ACCEPT = "application/x-jackson-smile, application/json; q=0.9";

    @RegisterExtension
    public static final UndertowServerExtension undertow = UndertowServerExtension.create()
            .jersey(ConjureJerseyFeature.INSTANCE)
            .jersey(new DirectResource())
            .jersey(new ProxyResource());

    @Test
    public void directEndpoint_servesJsonWhenSmileIsPreferred() {
        undertow.runRequest(
                ClassicRequestBuilder.get("/direct/value")
                        .addHeader(HttpHeaders.ACCEPT, SMILE_PREFERRED_ACCEPT)
                        .build(),
                response -> {
                    assertThat(response.getCode())
                            .as("@Produces(APPLICATION_JSON) keeps Smile out of the producible set, so JSON is served")
                            .isEqualTo(Status.OK.getStatusCode());
                    assertThat(response.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue())
                            .startsWith("application/json");
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("{\"value\":\"hello\"}");
                });
    }

    @Test
    public void proxyEndpoint_thatLostInheritedProducesAndReturnsOptional_failsToFallBackToJson() {
        undertow.runRequest(
                ClassicRequestBuilder.get("/proxy/value")
                        .addHeader(HttpHeaders.ACCEPT, SMILE_PREFERRED_ACCEPT)
                        .build(),
                // The sub-interface did not inherit @Produces, and an Optional return type is only produced by the
                // wildcard Java8OptionalMessageBodyWriter, so there is no concrete application/json producible to
                // out-rank the client's preferred Smile. The server selects application/x-jackson-smile, finds no
                // writer, and fails instead of falling back to JSON.
                response -> assertThat(response.getCode())
                        .as("no Smile writer is registered, so selecting Smile yields a server error")
                        .isEqualTo(Status.INTERNAL_SERVER_ERROR.getStatusCode()));
    }

    public static final class DirectResource implements SchemaLikeApi {
        @Override
        public Optional<ValueResponse> getValue() {
            return Optional.of(new ValueResponse("hello"));
        }
    }

    public static final class ProxyResource implements ProxyApi {
        @Override
        public Optional<ValueResponse> getValue() {
            return Optional.of(new ValueResponse("hello"));
        }
    }

    @Path("/direct")
    @Produces(MediaType.APPLICATION_JSON)
    public interface SchemaLikeApi {
        @GET
        @Path("/value")
        Optional<ValueResponse> getValue();
    }

    /** Re-declares {@code @Path} but not {@code @Produces}; the inherited JSON constraint is therefore lost. */
    @Path("/proxy")
    public interface ProxyApi extends SchemaLikeApi {}

    public static final class ValueResponse {
        private final String value;

        @JsonCreator
        ValueResponse(@JsonProperty("value") String value) {
            this.value = value;
        }

        @JsonProperty("value")
        public String getValue() {
            return value;
        }
    }
}
