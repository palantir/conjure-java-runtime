/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.http.errors;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import feign.Response;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.CheckForNull;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import org.junit.Test;

public final class SerializableErrorErrorDecoderTests {

    private static final SerializableErrorErrorDecoder decoder = SerializableErrorErrorDecoder.INSTANCE;

    @Test
    public void testJsonException() throws JsonProcessingException {
        Object error = SerializableError.of("msg", IllegalArgumentException.class, null);
        String json = new ObjectMapper().writeValueAsString(error);
        Response response = getResponse(MediaType.APPLICATION_JSON, json);
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(IllegalArgumentException.class)));
        assertThat(decode.getMessage(), is("msg"));
    }

    @Test
    public void testTextPlainException() {
        Response response = getResponse(MediaType.TEXT_PLAIN, "errorbody");
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is("Error 400. Reason: reason. Body:\nerrorbody"));
    }

    @Test
    public void testTextHtmlException() {
        Response response = getResponse(MediaType.TEXT_HTML, "errorbody");
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is("Error 400. Reason: reason. Body:\nerrorbody"));
    }

    @Test
    public void testNonTextException() {
        Response response = getResponse(MediaType.MULTIPART_FORM_DATA, "errorbody");
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is("Error 400. Reason: reason. Body content type: [multipart/form-data]"));
    }

    @Test
    public void testExceptionInErrorParsing() {
        Response response = getResponse(MediaType.APPLICATION_JSON, "notjsonifiable!");
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is(
                "Error 400. Reason: reason. Failed to parse error body: "
                        + "Unrecognized token 'notjsonifiable': was expecting 'null', 'true', 'false' or NaN\n "
                        + "at [Source: notjsonifiable!; line: 1, column: 15]. Body:\n"
                        + "notjsonifiable!"));
    }

    @Test
    public void testNullBody() {
        Response response = getResponse(MediaType.APPLICATION_JSON, null);
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is("400 reason"));
    }

    @Test
    public void testClientExceptionWrapsServerException() throws JsonProcessingException {
        Object error = SerializableError.of("msg", IllegalArgumentException.class,
                Lists.newArrayList(new RuntimeException().getStackTrace()));
        String json = new ObjectMapper().writeValueAsString(error);
        Response response = getResponse(MediaType.APPLICATION_JSON, json);
        Exception decode = decoder.decode("ignored", response);

        assertThat(decode, is(instanceOf(IllegalArgumentException.class)));
        assertThat(decode.getMessage(), is("msg"));
        assertThat(decode.getStackTrace()[0].getMethodName(), is("decode"));
        assertThat(decode.getCause(), is(instanceOf(IllegalArgumentException.class)));
        assertThat(decode.getCause().getMessage(), is("msg"));
        assertThat(decode.getCause().getStackTrace()[0].getMethodName(), is("testClientExceptionWrapsServerException"));
    }

    private static Response getResponse(String contentType, @CheckForNull String body) {
        return Response.create(400, "reason", ImmutableMap.<String, Collection<String>>of(HttpHeaders.CONTENT_TYPE,
                Collections.singletonList(contentType)), body, feign.Util.UTF_8);
    }

}
