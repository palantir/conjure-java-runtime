/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import com.google.common.net.HttpHeaders;
import feign.Response;
import feign.Response.Body;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SerializableErrorErrorDecoder implements ErrorDecoder {
    private static final Logger log = LoggerFactory.getLogger(SerializableErrorErrorDecoder.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        Collection<String> contentType = response.headers().get(HttpHeaders.CONTENT_TYPE);
        Body body = response.body();
        if (body != null) {
            String exceptionInErrorParsing = null;
            if (contentType.contains(MediaType.APPLICATION_JSON)) {
                try (InputStream in = body.asInputStream()) {
                    SerializableError error = MAPPER.readValue(in, SerializableError.class);
                    Class<? extends Exception> exceptionClass = error.getExceptionClass();
                    Exception exception = exceptionClass.getConstructor(String.class).newInstance(error.getMessage());
                    List<StackTraceElement> stackTrace = error.getStackTrace();
                    if (stackTrace != null) {
                        exception.setStackTrace(stackTrace.toArray(new StackTraceElement[stackTrace.size()]));
                    }
                    return exception;
                } catch (Exception e) {
                    log.error("Failed to parse error body as GenericError.", e);
                    exceptionInErrorParsing = e.getMessage();
                }
            } else if (contentType.contains(MediaType.TEXT_HTML) || contentType.contains(MediaType.TEXT_PLAIN)
                    || contentType.contains(MediaType.TEXT_XML)) {
                try (Reader bodyReader = body.asReader()) {
                    String responseString = CharStreams.toString(bodyReader);
                    return new RuntimeException(responseString);
                } catch (IOException e) {
                    log.error("Failed to parse error body as string.", e);
                    exceptionInErrorParsing = e.getMessage();
                }
            }

            String extra = exceptionInErrorParsing == null ? "" : ": " + exceptionInErrorParsing;
            String message = String.format("Server returned %s %s. Failed to parse error body%s", response.status(),
                    response.reason(), extra);
            return new RuntimeException(message);
        } else {
            return new RuntimeException(String.format("%s %s", response.status(), response.reason()));
        }
    }
}
