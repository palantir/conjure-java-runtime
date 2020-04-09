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

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.net.HttpHeaders;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.conjure.java.client.jaxrs.feignimpl.EndpointNameHeaderEnrichmentContract;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.HttpMethod;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.UrlBuilder;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import feign.Request;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
    private static final Splitter querySplitter = Splitter.on('&').omitEmptyStrings();
    private static final Splitter queryValueSplitter = Splitter.on('=');

    private final ConjureRuntime runtime;
    private final Channel channel;
    private final String baseUrl;
    private final String serviceName;
    private final String version;

    DialogueFeignClient(Class<?> jaxrsInterface, Channel channel, ConjureRuntime runtime, String baseUrl) {
        this.channel = Preconditions.checkNotNull(channel, "Channel is required");
        this.baseUrl = Preconditions.checkNotNull(baseUrl, "Base URL is required");
        this.runtime = Preconditions.checkNotNull(runtime, "ConjureRuntime is required");
        this.serviceName = Preconditions.checkNotNull(jaxrsInterface, "Service is required")
                .getSimpleName();
        this.version = Optional.ofNullable(jaxrsInterface.getPackage().getImplementationVersion())
                .orElse("0.0.0");
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

        try {
            return runtime.clients()
                    .block(runtime.clients()
                            .call(
                                    channel,
                                    new FeignRequestEndpoint(request),
                                    builder.build(),
                                    FeignResponseDeserializer.INSTANCE));
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
        if (EndpointNameHeaderEnrichmentContract.ENDPOINT_NAME_HEADER.equalsIgnoreCase(headerName)) {
            return false;
        }
        return true;
    }

    private static String urlDecode(String input) {
        try {
            return URLDecoder.decode(input, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new SafeRuntimeException("Failed to decode path segment", e, UnsafeArg.of("encoded", input));
        }
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
        }
        return Optional.of(new ByteArrayRequestBody(
                requestBodyContent,
                // A Content-Type header was not present on feign request
                // with a body, use the default application/json.
                maybeContentType.orElse("application/json")));
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

        @Override
        public String toString() {
            return "DialogueResponseBody{response=" + response + '}';
        }
    }

    enum FeignResponseDeserializer implements Deserializer<feign.Response> {
        INSTANCE;

        @Override
        public feign.Response deserialize(Response response) {
            return feign.Response.create(
                    response.code(),
                    null,
                    Multimaps.asMap((Multimap<String, String>) response.headers()),
                    new DialogueResponseBody(response));
        }

        @Override
        public Optional<String> accepts() {
            // The Accept header is already set by Feign based on method annotations and needn't be overridden.
            return Optional.empty();
        }
    }

    /** Converts back from a feign response into a dialogue response for exception mapping. */
    private static final class FeignDialogueResponse implements Response {

        private final feign.Response delegate;

        FeignDialogueResponse(feign.Response delegate) {
            this.delegate = delegate;
        }

        @Override
        public InputStream body() {
            feign.Response.Body body = delegate.body();
            if (body != null) {
                try {
                    return body.asInputStream();
                } catch (IOException e) {
                    throw new SafeRuntimeException("Failed to access the delegate response body", e);
                }
            }
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int code() {
            return delegate.status();
        }

        @Override
        public ListMultimap<String, String> headers() {
            ListMultimap<String, String> result = MultimapBuilder.treeKeys(String.CASE_INSENSITIVE_ORDER)
                    .arrayListValues()
                    .build();
            delegate.headers().forEach(result::putAll);
            return result;
        }

        @Override
        public Optional<String> getFirstHeader(String header) {
            return Optional.ofNullable(
                    Iterables.getFirst(delegate.headers().getOrDefault(header, Collections.emptyList()), null));
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public String toString() {
            return "FeignDialogueResponse{delegate=" + delegate + '}';
        }
    }

    /** Implements exception handling equivalent dialogue decoders. */
    static final class RemoteExceptionDecoder implements feign.codec.ErrorDecoder {

        private final ConjureRuntime runtime;

        RemoteExceptionDecoder(ConjureRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public Exception decode(String _methodKey, feign.Response response) {
            try {
                // The dialogue empty body deserializer properly handles exception mapping
                runtime.bodySerDe().emptyBodyDeserializer().deserialize(new FeignDialogueResponse(response));
            } catch (Exception e) {
                return e;
            }
            return new UnknownRemoteException(response.status(), "<unknown>");
        }
    }

    private final class FeignRequestEndpoint implements Endpoint {
        private final feign.Request request;
        private final String endpoint;
        private final HttpMethod method;

        FeignRequestEndpoint(feign.Request request) {
            this.request = request;
            this.method = HttpMethod.valueOf(request.method().toUpperCase(Locale.ENGLISH));
            endpoint = getFirstHeader(request, EndpointNameHeaderEnrichmentContract.ENDPOINT_NAME_HEADER)
                    .orElse("feign");
        }

        @Override
        public void renderPath(Map<String, String> _params, UrlBuilder url) {
            String target = request.url();
            Preconditions.checkState(
                    target.startsWith(baseUrl),
                    "Request URL must start with base url",
                    UnsafeArg.of("requestUrl", target),
                    UnsafeArg.of("baseUrl", baseUrl));
            String trailing = target.substring(baseUrl.length());
            int queryParamsStart = trailing.indexOf('?');
            String queryPortion = queryParamsStart == -1 ? trailing : trailing.substring(0, queryParamsStart);
            for (String pathSegment : pathSplitter.split(queryPortion)) {
                url.pathSegment(urlDecode(pathSegment));
            }
            if (queryParamsStart != -1) {
                String querySegments = trailing.substring(queryParamsStart + 1);
                for (String querySegment : querySplitter.split(querySegments)) {
                    List<String> keyValuePair = queryValueSplitter.splitToList(querySegment);
                    if (keyValuePair.size() != 2) {
                        throw new SafeIllegalStateException(
                                "Expected two parameters",
                                SafeArg.of("parameters", keyValuePair.size()),
                                UnsafeArg.of("values", keyValuePair));
                    }
                    url.queryParam(urlDecode(keyValuePair.get(0)), urlDecode(keyValuePair.get(1)));
                }
            }
        }

        @Override
        public HttpMethod httpMethod() {
            return method;
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
    }
}
