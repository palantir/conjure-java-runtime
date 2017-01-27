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

package com.palantir.remoting2.errors;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.junit.Test;

public final class SerializableErrorErrorDecoderTests {

    private static final String message = "hello";
    private static final int STATUS_42 = 42;
    private static final ObjectMapper OBJECT_MAPPER = ObjectMappers.guavaJdk7Jdk8();

    @Test
    public void testWebApplicationExceptions() {
        testEncodingAndDecodingWebException(ClientErrorException.class, Status.NOT_ACCEPTABLE);
        testEncodingAndDecodingWebException(ServerErrorException.class, Status.BAD_GATEWAY);
        testEncodingAndDecodingWebException(WebApplicationException.class, Status.NOT_MODIFIED);
    }

    @Test
    public void testNotAuthorizedException() throws Exception {
        NotAuthorizedException originalException =
                new NotAuthorizedException(message, Response.status(Status.UNAUTHORIZED).build());

        RemoteException exception = (RemoteException) encodeAndDecode(originalException);
        assertThat(exception.getCause(), is(nullValue()));
        assertThat(exception.getStatus(), is(Status.UNAUTHORIZED.getStatusCode()));
        assertThat(exception.getRemoteException().getErrorName(), is(NotAuthorizedException.class.getName()));
        assertThat(exception.getMessage(), is(message));
    }

    @Test
    public void testSpecificException() {
        Exception exception = encodeAndDecode(new IllegalArgumentException("msg"));
        assertThat(exception, is(instanceOf(RemoteException.class)));
        assertThat(exception.getMessage(), is("msg"));
    }

    @Test
    public void testTextPlainException() {
        Exception decode = decode(MediaType.TEXT_PLAIN, STATUS_42, "errorbody");
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is("Error 42. Reason: reason. Body:\nerrorbody"));
    }

    @Test
    public void testTextHtmlException() {
        Exception decode = decode(MediaType.TEXT_HTML, STATUS_42, "errorbody");
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is("Error 42. Reason: reason. Body:\nerrorbody"));
    }

    @Test
    public void testNonTextException() {
        Exception decode = decode(MediaType.MULTIPART_FORM_DATA, STATUS_42, "errorbody");
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(),
                is("Error 42. Reason: reason. Body:\nerrorbody"));
    }

    @Test
    public void testExceptionInErrorParsing() {
        Exception decode = decode(MediaType.APPLICATION_JSON, STATUS_42, "notjsonifiable!");
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is(
                "Error 42. Reason: reason. Failed to parse error body and deserialize exception: "
                        + "Unrecognized token 'notjsonifiable': was expecting 'null', 'true', 'false' or NaN\n "
                        + "at [Source: notjsonifiable!; line: 1, column: 15]. Body:\n"
                        + "notjsonifiable!"));
    }

    @Test
    public void testNullBody() {
        Exception decode = decode(MediaType.APPLICATION_JSON, STATUS_42, null);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is("42 reason"));
    }

    @Test
    public void testRemoteExceptionCarriesSerializedError() throws IOException {
        Object error = SerializableError.of("msg", IllegalArgumentException.class,
                Lists.newArrayList(new RuntimeException().getStackTrace()));
        String json = OBJECT_MAPPER.writeValueAsString(error);
        RemoteException decode = (RemoteException) decode(MediaType.APPLICATION_JSON, STATUS_42, json);

        assertThat(decode.getMessage(), is("msg"));
        assertThat(decode.getStatus(), is(STATUS_42));
        assertThat(decode.getStackTrace()[0].getMethodName(), is("getException"));
        assertThat(decode.getCause(), is(nullValue()));
        assertThat(decode.getRemoteException().getErrorName(), is(IllegalArgumentException.class.getName()));
        assertThat(decode.getRemoteException().getMessage(), is("msg"));
        assertThat(decode.getRemoteException().getStackTrace().get(0).getMethodName(),
                is(Optional.of("testRemoteExceptionCarriesSerializedError")));
    }

    @Test
    public void testRemoteException_stackTraceSerializationIsCompatibleWithJavaStackTrace() throws IOException {
        SerializableError error = SerializableError.of("msg", IllegalArgumentException.class,
                Lists.newArrayList(new RuntimeException().getStackTrace()));
        String json = OBJECT_MAPPER.writeValueAsString(error);
        RemoteException decode = (RemoteException) decode(MediaType.APPLICATION_JSON, STATUS_42, json);

        SerializableStackTraceElement element = decode.getRemoteException().getStackTrace().get(0);
        StackTraceElement stackTrace = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsBytes(element),
                StackTraceElement.class);
        assertThat(stackTrace.getMethodName(), is(element.getMethodName().get()));
        assertThat(stackTrace.getClassName(), is(element.getClassName().get()));
        assertThat(stackTrace.getFileName(), is(element.getFileName().get()));
        assertThat(stackTrace.getLineNumber(), is(element.getLineNumber().get()));
    }

    @Test
    public void testRemoteExceptionIgnoresUnkownProperties() throws Exception {
        String stackTrace = "{\"methodName\" : \"methodName\", \"fileName\" : \"fileName\", \"lineNumber\" : 0,"
                + "\"className\" : \"className\" , \"noSuchProperty\" : false }";
        String error = "{\"message\": \"message\", \"exceptionClass\": \"exceptionClass\","
                + "\"stackTrace\": [" + stackTrace + "], \"noSuchProperty\": \"foo\"}";

        OBJECT_MAPPER.readValue(error, SerializableError.class);
    }

    private static Exception encodeAndDecode(Exception exception) {
        Object error = SerializableError.of(exception.getMessage(), exception.getClass(), null);
        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        int status = (exception instanceof WebApplicationException)
                ? ((WebApplicationException) exception).getResponse().getStatus()
                : 400;
        return decode(MediaType.APPLICATION_JSON, json, status);
    }

    private static Exception decode(String contentType, int status, @CheckForNull String body) {
        return SerializableErrorToExceptionConverter.getException(
                Collections.singletonList(contentType),
                status,
                "reason",
                body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static Exception decode(String contentType, @CheckForNull String body, int status) {
        return SerializableErrorToExceptionConverter.getException(Collections.singletonList(contentType), status,
                "reason", body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static void testEncodingAndDecodingWebException(Class<? extends WebApplicationException> exceptionClass,
            Status status) {
        WebApplicationException exceptionToProcess;
        try {
            exceptionToProcess = exceptionClass.getConstructor(String.class, Status.class).newInstance(message, status);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }

        RemoteException exception = (RemoteException) encodeAndDecode(exceptionToProcess);
        assertThat(exception.getCause(), is(nullValue()));
        assertThat(exception.getStatus(), is(status.getStatusCode()));
        assertThat(exception.getRemoteException().getErrorName(), is(exceptionClass.getName()));
        assertThat(exception.getMessage(), is(message));
    }

}
