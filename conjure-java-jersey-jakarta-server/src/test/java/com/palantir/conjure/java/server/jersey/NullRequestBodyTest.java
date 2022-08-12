/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.undertest.UndertowServerExtension;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class NullRequestBodyTest {

    @RegisterExtension
    public static final UndertowServerExtension undertow = UndertowServerExtension.create()
            .jersey(ConjureJerseyFeature.INSTANCE)
            .jersey(new TestResource());

    @Test
    public void testEmptyRequestBody() {
        // this endpoint does not have any annotation
        undertow.runRequest(
                ClassicRequestBuilder.post("/post")
                        .setHeader("Content-Type", "application/json")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(204);
                });

        // this endpoint has the @NotNull annotation
        undertow.runRequest(
                ClassicRequestBuilder.post("/post-not-null")
                        .setHeader("Content-Type", "application/json")
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(400);
                });
    }

    @Test
    public void testExplicitlyNullRequestBody() {
        // this endpoint does not have any annotation
        undertow.runRequest(
                ClassicRequestBuilder.post("/post")
                        .setEntity(new StringEntity("null", ContentType.APPLICATION_JSON))
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(204);
                });

        // this endpoint has the @NotNull annotation
        undertow.runRequest(
                ClassicRequestBuilder.post("/post-not-null")
                        .setEntity(new StringEntity("null", ContentType.APPLICATION_JSON))
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(400);
                });
    }

    @Test
    public void testNonNullRequestBody() {
        // this endpoint's handler method does not throw
        undertow.runRequest(
                ClassicRequestBuilder.post("/post")
                        .setEntity(new StringEntity("{}", ContentType.APPLICATION_JSON))
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(204);
                });

        // this endpoint's handler method throws -> 500
        undertow.runRequest(
                ClassicRequestBuilder.post("/post-not-null")
                        .setEntity(new StringEntity("{}", ContentType.APPLICATION_JSON))
                        .build(),
                response -> {
                    assertThat(response.getCode()).isEqualTo(500);
                });
    }

    public static final class TestResource implements TestService {
        @Override
        public void postRequestBody(Map<String, String> _data) {}

        @Override
        public void postRequestBodyNotNull(Map<String, String> _data) {
            throw new RuntimeException("oh no");
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TestService {

        @POST
        @Path("/post")
        void postRequestBody(Map<String, String> data);

        @POST
        @Path("/post-not-null")
        void postRequestBodyNotNull(@NotNull Map<String, String> data);
    }
}
