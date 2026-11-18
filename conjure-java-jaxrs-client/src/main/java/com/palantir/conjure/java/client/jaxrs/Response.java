/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * An immutable response to an http invocation which only returns string content.
 */
final class Response implements Closeable {

    private final int status;
    private final Map<String, Collection<String>> headers;
    private final Body body;

    private Response(int status, Map<String, Collection<String>> headers, Body body) {
        Util.checkState(status >= 200, "Invalid status code: %s", status);
        this.status = status;
        this.headers = Collections.unmodifiableMap(caseInsensitiveCopyOf(headers));
        this.body = body;
    }

    static Response create(int status, Map<String, Collection<String>> headers, byte[] data) {
        return new Response(status, headers, new ByteArrayBody(data));
    }

    static Response create(int status, Map<String, Collection<String>> headers, Body body) {
        return new Response(status, headers, body);
    }

    /**
     * status code. ex {@code 200}
     *
     * See <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html" >rfc2616</a>
     */
    int status() {
        return status;
    }

    /**
     * Returns a case-insensitive mapping of header names to their values.
     */
    Map<String, Collection<String>> headers() {
        return headers;
    }

    /**
     * if present, the response had a body
     */
    Body body() {
        return body;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("HTTP/1.1 ").append(status);
        builder.append('\n');
        for (String field : headers.keySet()) {
            for (String value : Util.valuesOrEmpty(headers, field)) {
                builder.append(field).append(": ").append(value).append('\n');
            }
        }
        if (body != null) {
            builder.append('\n').append(body);
        }
        return builder.toString();
    }

    @Override
    public void close() {
        Util.ensureClosed(body);
    }

    interface Body extends Closeable {

        /**
         * length in bytes, if known. Null if unknown or greater than {@link Integer#MAX_VALUE}.
         *
         * <br><br><br><b>Note</b><br> This is an integer as
         * most implementations cannot do bodies greater than 2GB.
         */
        Integer length();

        /**
         * It is the responsibility of the caller to close the stream.
         */
        InputStream asInputStream() throws IOException;

        /**
         * It is the responsibility of the caller to close the stream.
         */
        Reader asReader() throws IOException;
    }

    private static final class ByteArrayBody implements Body {

        private final byte[] data;

        ByteArrayBody(byte[] data) {
            this.data = data;
        }

        @Override
        public Integer length() {
            return data.length;
        }

        @Override
        public InputStream asInputStream() throws IOException {
            return new ByteArrayInputStream(data);
        }

        @Override
        public Reader asReader() throws IOException {
            return new InputStreamReader(asInputStream(), StandardCharsets.UTF_8);
        }

        @Override
        public void close() throws IOException {}

        @Override
        public String toString() {
            return Util.decodeOrDefault(data, StandardCharsets.UTF_8, "Binary data");
        }
    }

    private static Map<String, Collection<String>> caseInsensitiveCopyOf(Map<String, Collection<String>> headers) {
        Map<String, Collection<String>> result = new TreeMap<String, Collection<String>>(String.CASE_INSENSITIVE_ORDER);

        for (Map.Entry<String, Collection<String>> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            if (!result.containsKey(headerName)) {
                result.put(headerName.toLowerCase(Locale.ROOT), new ArrayList<>());
            }
            result.get(headerName).addAll(entry.getValue());
        }
        return result;
    }
}
