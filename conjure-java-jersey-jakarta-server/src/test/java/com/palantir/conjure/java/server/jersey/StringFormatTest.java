/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public final class StringFormatTest {

    @RegisterExtension
    public static final UndertowServerExtension undertow = UndertowServerExtension.create()
            .jersey(ConjureJerseyFeature.INSTANCE)
            .jersey(new StringFormatTest.TestResource());

    @Test
    public void testTextPlainMediaTypeReturnsPlainStrings() {
        undertow.get("/textString", response -> {
            assertThat(response.getCode()).isEqualTo(204);
        });

        undertow.runRequest(
                ClassicRequestBuilder.get("/textString")
                        .addParameter("value", "val")
                        .build(),
                response -> {
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("val");
                });
    }

    @Test
    public void testJsonMediaTypeReturnsPlainStrings() {
        // This behaviour is somewhat unexpected since a valid JSON response object would be "\"val\"" rather than "val"
        undertow.get("/jsonString", response -> {
            assertThat(response.getCode()).isEqualTo(204);
        });
        undertow.runRequest(
                ClassicRequestBuilder.get("/jsonString")
                        .addParameter("value", "val")
                        .build(),
                response -> {
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("val");
                });
    }

    @Test
    public void testJsonStringBodyDeserializedAsPlainString() {
        // This behaviour is somewhat unexpected since a valid JSON string is deserialized as a raw string, even
        // though the endpoint consumes application/json
        undertow.runRequest(ClassicRequestBuilder.post("/bodyString").build(), response -> {
            assertThat(EntityUtils.toString(response.getEntity())).isEmpty();
        });

        undertow.runRequest(
                ClassicRequestBuilder.post("/bodyString")
                        .setEntity(new StringEntity("\"val\"", ContentType.APPLICATION_JSON))
                        .build(),
                response -> {
                    assertThat(EntityUtils.toString(response.getEntity())).isEqualTo("\"val\"");
                });
    }

    public static final class TestResource implements TestService {
        @Override
        public String getJsonString(@Nullable String value) {
            return value;
        }

        @Override
        public String getTextString(@Nullable String value) {
            return value;
        }

        @Override
        public String postJsonString(String value) {
            return value;
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public interface TestService {

        @GET
        @Path("/jsonString")
        @Produces(MediaType.APPLICATION_JSON)
        String getJsonString(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/textString")
        @Produces(MediaType.TEXT_PLAIN)
        String getTextString(@QueryParam("value") @Nullable String value);

        @POST
        @Path("/bodyString")
        @Produces(MediaType.TEXT_PLAIN)
        String postJsonString(@Nullable String value);
    }
}
