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

package com.palantir.conjure.java.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting.api.errors.ErrorType;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting.api.errors.ServiceException;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
    private static final ObjectMapper CLIENT_MAPPER = ObjectMappers.newClientObjectMapper();
    private static final ObjectMapper SERVER_MAPPER = ObjectMappers.newServerObjectMapper();

    @Test
    public void testWebApplicationExceptions() {
        testEncodingAndDecodingWebException(ClientErrorException.class, Status.NOT_ACCEPTABLE);
        testEncodingAndDecodingWebException(ServerErrorException.class, Status.BAD_GATEWAY);
        testEncodingAndDecodingWebException(WebApplicationException.class, Status.NOT_MODIFIED);
    }

    @Test
    public void testServiceException() throws Exception {
        ServiceException originalException =
                new ServiceException(ErrorType.FAILED_PRECONDITION, SafeArg.of("key", "value"));

        SerializableError error = SerializableError.forException(originalException);
        String json = SERVER_MAPPER.writeValueAsString(error);
        RemoteException exception = (RemoteException) decode(MediaType.APPLICATION_JSON, json, 400);
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getStatus()).isEqualTo(400);
        assertThat(exception.getError().errorCode()).isEqualTo(ErrorType.FAILED_PRECONDITION.code().name());
        assertThat(exception.getError().errorName()).isEqualTo(ErrorType.FAILED_PRECONDITION.name());
        assertThat(exception.getMessage()).isEqualTo("RemoteException: " + ErrorType.FAILED_PRECONDITION.code().name()
                + " (" + ErrorType.FAILED_PRECONDITION.name() + ") with instance ID " + error.errorInstanceId());
    }

    @Test
    public void testNotAuthorizedException() throws Exception {
        NotAuthorizedException originalException =
                new NotAuthorizedException(message, Response.status(Status.UNAUTHORIZED).build());

        RemoteException exception = (RemoteException) encodeAndDecode(originalException);
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getStatus()).isEqualTo(Status.UNAUTHORIZED.getStatusCode());
        assertThat(exception.getError().errorCode()).isEqualTo(NotAuthorizedException.class.getName());
        assertThat(exception.getError().errorName()).isEqualTo(message);
        assertThat(exception.getMessage())
                .isEqualTo("RemoteException: javax.ws.rs.NotAuthorizedException (" + message + ") with instance ID "
                        + exception.getError().errorInstanceId());
    }

    @Test
    public void testSpecificException() {
        Exception exception = encodeAndDecode(new IllegalArgumentException("msg"));
        assertThat(exception).isInstanceOf(RemoteException.class);
        assertThat(exception.getMessage()).startsWith("RemoteException: java.lang.IllegalArgumentException (msg)");
    }

    @Test
    public void testTextPlainException() {
        Exception decode = decode(MediaType.TEXT_PLAIN, STATUS_42, "errorbody");
        assertThat(decode).isInstanceOf(RuntimeException.class);
        assertThat(decode.getMessage()).isEqualTo("Error 42. Body:\nerrorbody");
    }

    @Test
    public void testTextHtmlException() {
        Exception decode = decode(MediaType.TEXT_HTML, STATUS_42, "errorbody");
        assertThat(decode).isInstanceOf(RuntimeException.class);
        assertThat(decode.getMessage()).isEqualTo("Error 42. Body:\nerrorbody");
    }

    @Test
    public void testNonTextException() {
        Exception decode = decode(MediaType.MULTIPART_FORM_DATA, STATUS_42, "errorbody");
        assertThat(decode).isInstanceOf(RuntimeException.class);
        assertThat(decode.getMessage()).isEqualTo("Error 42. Body:\nerrorbody");
    }

    @Test
    public void testExceptionInErrorParsing() {
        Exception decode = decode(MediaType.APPLICATION_JSON, STATUS_42, "notjsonifiable!");
        assertThat(decode).isInstanceOf(RuntimeException.class);
        assertThat(decode.getMessage()).isEqualTo(
                "Error 42. Failed to parse error body and deserialize exception: "
                        + "Unrecognized token 'notjsonifiable': was expecting 'null', 'true', 'false' or NaN\n "
                        + "at [Source: (String)\"notjsonifiable!\"; line: 1, column: 15]. Body:\n"
                        + "notjsonifiable!");
    }

    @Test
    public void testNullBody() {
        Exception decode = decode(MediaType.APPLICATION_JSON, STATUS_42, null);
        assertThat(decode).isInstanceOf(RuntimeException.class);
        assertThat(decode.getMessage()).isEqualTo("42");
    }

    @Test
    public void testRemoteExceptionIgnoresUnknownProperties() throws Exception {
        String stackTrace = "{\"methodName\" : \"methodName\", \"fileName\" : \"fileName\", \"lineNumber\" : 0,"
                + "\"className\" : \"className\" , \"noSuchProperty\" : false }";
        String error = "{\"message\": \"message\", \"exceptionClass\": \"exceptionClass\","
                + "\"stackTrace\": [" + stackTrace + "], \"noSuchProperty\": \"foo\"}";

        CLIENT_MAPPER.readValue(error, SerializableError.class);
    }

    private static Exception encodeAndDecode(Exception exception) {
        Preconditions.checkArgument(!(exception instanceof ServiceException), "Use SerializableError#forException");
        Object error = SerializableError.builder()
                .errorCode(exception.getClass().getName())
                .errorName(exception.getMessage())
                .build();
        String json;
        try {
            json = SERVER_MAPPER.writeValueAsString(error);
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
                body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static Exception decode(String contentType, @CheckForNull String body, int status) {
        return SerializableErrorToExceptionConverter.getException(Collections.singletonList(contentType), status,
                body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
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
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getStatus()).isEqualTo(status.getStatusCode());
        assertThat(exception.getError().errorCode()).isEqualTo(exceptionClass.getName());
        assertThat(exception.getMessage())
                .startsWith("RemoteException: " + exceptionClass.getName() + " (" + message + ")");
    }

}
