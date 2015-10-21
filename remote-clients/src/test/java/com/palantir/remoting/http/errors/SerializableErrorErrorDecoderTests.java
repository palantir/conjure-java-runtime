/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http.errors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import feign.Response;
import java.util.Arrays;
import java.util.Collection;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import org.junit.Test;

public final class SerializableErrorErrorDecoderTests {

    private static final SerializableErrorErrorDecoder decoder = new SerializableErrorErrorDecoder();

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
        assertThat(decode.getMessage(), is("errorbody"));
    }

    @Test
    public void testTextHtmlException() {
        Response response = getResponse(MediaType.TEXT_HTML, "errorbody");
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is("errorbody"));
    }

    @Test
    public void testNonTextException() {
        Response response = getResponse(MediaType.MULTIPART_FORM_DATA, "errorbody");
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is("Server returned 400 reason. Failed to parse error body"));
    }

    @Test
    public void testExceptionInErrorParsing() {
        Response response = getResponse(MediaType.APPLICATION_JSON, "notjsonifiable!");
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), containsString(
                "Server returned 400 reason. Failed to parse error body: "
                        + "Unrecognized token 'notjsonifiable': was expecting 'null', "
                        + "'true', 'false' or NaN\n at [Source: "));
    }

    @Test
    public void testNullBody() {
        Response response = getResponse(MediaType.APPLICATION_JSON, null);
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is("400 reason"));
    }

    private static Response getResponse(String contentType, String body) {
        return Response.create(400, "reason", ImmutableMap.<String, Collection<String>>of(
                HttpHeaders.CONTENT_TYPE, Arrays.asList(contentType)), body, feign.Util.UTF_8);
    }

}
