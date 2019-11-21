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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.testing.Assertions;
import feign.Request;
import feign.Request.Body;
import feign.Request.HttpMethod;
import feign.Response;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

public final class NeverReturnNullDecoderTest extends TestBase {

    private static final Request REQUEST = Request.create(
            HttpMethod.GET,
            "",
            ImmutableMap.of(),
            Body.empty(),
            null);

    private final Decoder textDelegateDecoder = new NeverReturnNullDecoder(
            new JacksonDecoder(ObjectMappers.newClientObjectMapper()));

    @Test
    public void throws_nullpointerexception_when_body_is_null() {
        Response response = Response.builder()
                .request(REQUEST)
                .status(200)
                .reason("OK")
                .body(null, StandardCharsets.UTF_8)
                .build();

        Assertions.assertThatLoggableExceptionThrownBy(() -> textDelegateDecoder.decode(response, List.class))
                .isInstanceOf(NullPointerException.class)
                .hasLogMessage("Unexpected null body")
                .containsArgs(SafeArg.of("status", 200));
    }

    @Test
    public void throws_nullpointerexception_when_body_is_string_null() {
        Response response = Response.builder()
                .request(REQUEST)
                .status(200)
                .reason("OK")
                .body("null", StandardCharsets.UTF_8)
                .build();

        Assertions.assertThatLoggableExceptionThrownBy(() -> textDelegateDecoder.decode(response, List.class))
                .isInstanceOf(NullPointerException.class)
                .hasLogMessage("Unexpected null body")
                .containsArgs(SafeArg.of("status", 200));
    }

    @Test
    public void throws_nullpointerexception_when_body_is_empty_string() {
        Response response = Response.builder()
                .request(REQUEST)
                .status(200)
                .reason("OK")
                .body("", StandardCharsets.UTF_8)
                .build();

        Assertions.assertThatLoggableExceptionThrownBy(() -> textDelegateDecoder.decode(response, List.class))
                .isInstanceOf(NullPointerException.class)
                .hasLogMessage("Unexpected null body")
                .containsArgs(SafeArg.of("status", 200));
    }

    @Test
    public void works_fine_when_body_is_not_null() throws Exception {
        Response response = Response.builder()
                .request(REQUEST)
                .status(200)
                .reason("OK")
                .body("[1, 2, 3]", StandardCharsets.UTF_8)
                .build();

        Object decodedObject = textDelegateDecoder.decode(response, List.class);
        assertThat(decodedObject).isEqualTo(ImmutableList.of(1, 2, 3));
    }
}
