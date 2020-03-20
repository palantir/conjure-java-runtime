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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.io.CharStreams;
import com.google.common.net.HttpHeaders;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Clients;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import feign.Request;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@link DialogueFeignClient} is an adapter from {@link feign.Client} to {@link Channel Dialogue Channel}
 * taking advantage of the superior observability and stability provided by Dialogue.
 */
final class DialogueFeignClient implements feign.Client {

    private static final String PATH_TEMPLATE = "hr-path-template";
    private static final Splitter pathSplitter = Splitter.on('/').omitEmptyStrings();
    private static final Splitter.MapSplitter querySplitter =
            Splitter.on('&').omitEmptyStrings().withKeyValueSeparator('=');

    private final Clients clients;
    private final Channel channel;
    private final String baseUrl;
    private final String serviceName;
    private final String version;

    DialogueFeignClient(Class<?> jaxrsInterface, Channel channel, ConjureRuntime runtime, String baseUrl) {
        this.channel = new ErrorDecoderChannel(Preconditions.checkNotNull(channel, "Channel is required"));
        this.serviceName = jaxrsInterface.getSimpleName();
        this.version = Optional.ofNullable(jaxrsInterface.getPackage().getImplementationVersion())
                .orElse("0.0.0");
        this.baseUrl = baseUrl;
        this.clients = runtime.clients();
    }

    @Override
    public feign.Response execute(Request request, Request.Options _options) throws IOException {
        com.palantir.dialogue.Request.Builder builder =
                com.palantir.dialogue.Request.builder().body(requestBody(request));
        request.headers().forEach((headerName, values) -> {
            if (includeRequestHeader(headerName)) {
                builder.putAllHeaderParams(headerName, values);
            }
        });
        String target = request.url();
        Preconditions.checkState(
                target.startsWith(baseUrl), "Request URL must start with base url", UnsafeArg.of("baseUrl", baseUrl));
        String trailing = target.substring(baseUrl.length());
        Endpoint endpoint = new Endpoint() {

            private final String endpoint =
                    getFirstHeader(request, PATH_TEMPLATE).orElse("feign");

            @Override
            public void renderPath(Map<String, String> _params, UrlBuilder url) {
                int queryParamsStart = trailing.indexOf('?');
                String queryPortion = queryParamsStart == -1 ? trailing : trailing.substring(0, queryParamsStart);
                for (String pathSegment : pathSplitter.split(queryPortion)) {
                    url.pathSegment(urlDecode(pathSegment));
                }
                if (queryParamsStart != -1) {
                    String querySegments = trailing.substring(queryParamsStart + 1);
                    querySplitter
                            .split(querySegments)
                            .forEach((name, value) -> url.queryParam(urlDecode(name), urlDecode(value)));
                }
            }

            @Override
            public HttpMethod httpMethod() {
                return HttpMethod.valueOf(request.method().toUpperCase(Locale.ENGLISH));
            }

            @Override
            public String serviceName() {
                return serviceName;
            }

            @Override
            public String endpointName() {
                return endpoint;
            }

            @Override
            public String version() {
                return version;
            }
        };
        try {
            Response response =
                    clients.block(clients.call(channel, endpoint, builder.build(), IdentityDeserializer.INSTANCE));
            return feign.Response.create(
                    response.code(),
                    null,
                    Multimaps.asMap((Multimap<String, String>) response.headers()),
                    new DialogueResponseBody(response));
        } catch (UncheckedExecutionException e) {
            // Rethrow IOException to match standard feign behavior
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw e;
        }
    }

    private static boolean includeRequestHeader(String headerName) {
        // Content-type and content-length headers are handled by requestBody
        if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(headerName)) {
            return false;
        }
        if (HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(headerName)) {
            return false;
        }
        // Tracing path template is informational only
        if (PATH_TEMPLATE.equalsIgnoreCase(headerName)) {
            return false;
        }
        return true;
    }

    private static String urlDecode(String input) {
        if (input.contains("%")) {
            try {
                return URLDecoder.decode(input, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new SafeRuntimeException("Failed to decode path segment", e);
            }
        }
        return input;
    }

    private static Optional<RequestBody> requestBody(Request request) {
        byte[] requestBodyContent = request.body();
        if (requestBodyContent == null) {
            return Optional.empty();
        }
        Optional<String> maybeContentType = getFirstHeader(request, HttpHeaders.CONTENT_TYPE);
        if (!maybeContentType.isPresent()) {
            if (requestBodyContent.length == 0) {
                return Optional.empty();
            }
            throw new SafeIllegalStateException("A Content-Type header was not present on feign request with a body");
        }
        return Optional.of(new ByteArrayRequestBody(requestBodyContent, maybeContentType.get()));
    }

    private static Optional<String> getFirstHeader(Request request, String name) {
        Collection<String> values = request.headers().get(name);
        if (values != null) {
            return Optional.ofNullable(Iterables.getFirst(values, null));
        }
        return Optional.empty();
    }

    private static final class ByteArrayRequestBody implements RequestBody {

        private final byte[] buffer;
        private final String contentType;

        ByteArrayRequestBody(byte[] buffer, String contentType) {
            this.buffer = buffer;
            this.contentType = contentType;
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {
            output.write(buffer);
        }

        @Override
        public String contentType() {
            return contentType;
        }

        @Override
        public boolean repeatable() {
            return true;
        }

        @Override
        public void close() {
            // nothing to do
        }
    }

    private static final class DialogueResponseBody implements feign.Response.Body {

        private final Response response;

        DialogueResponseBody(Response response) {
            this.response = response;
        }

        @Override
        public Integer length() {
            return response.getFirstHeader(HttpHeaders.CONTENT_LENGTH)
                    .map(Ints::tryParse)
                    .orElse(null);
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public InputStream asInputStream() {
            return response.body();
        }

        @Override
        public Reader asReader() {
            return new InputStreamReader(asInputStream(), StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            response.close();
        }
    }

    enum IdentityDeserializer implements Deserializer<Response> {
        INSTANCE;

        @Override
        public Response deserialize(Response response) {
            return response;
        }

        @Override
        public Optional<String> accepts() {
            return Optional.empty();
        }
    }

    /** Implements exception handling equivalent dialogue decoders. */
    static final class ErrorDecoderChannel implements Channel {

        private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();

        private final Channel delegate;

        ErrorDecoderChannel(Channel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, com.palantir.dialogue.Request request) {
            return Futures.transformAsync(
                    delegate.execute(endpoint, request),
                    response -> {
                        if (isError(response)) {
                            return Futures.immediateFailedFuture(decode(response));
                        }
                        return Futures.immediateFuture(response);
                    },
                    MoreExecutors.directExecutor());
        }

        boolean isError(Response response) {
            return 300 <= response.code() && response.code() <= 599;
        }

        private RemoteException decode(Response response) {
            String body;
            try {
                body = toString(response.body());
            } catch (NullPointerException | IOException e) {
                UnknownRemoteException exception = new UnknownRemoteException(response.code(), "<unparseable>");
                exception.initCause(e);
                throw exception;
            }

            Optional<String> contentType = response.getFirstHeader(javax.ws.rs.core.HttpHeaders.CONTENT_TYPE);
            if (contentType.isPresent()
                    && contentType.get().toLowerCase(Locale.ENGLISH).startsWith("application/json")) {
                try {
                    SerializableError serializableError = MAPPER.readValue(body, SerializableError.class);
                    return new RemoteException(serializableError, response.code());
                } catch (Exception e) {
                    throw new UnknownRemoteException(response.code(), body);
                }
            }

            throw new UnknownRemoteException(response.code(), body);
        }

        private static String toString(InputStream body) throws IOException {
            try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
                return CharStreams.toString(reader);
            }
        }
    }
}
