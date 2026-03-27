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
package com.palantir.conjure.java.client.jaxrs.feign;

import static com.palantir.conjure.java.client.jaxrs.feign.Util.checkNotNull;
import static com.palantir.conjure.java.client.jaxrs.feign.Util.valuesOrEmpty;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/**
 * An immutable request to an http server.
 */
public final class Request {

    /**
     * No parameters can be null except {@code body} and {@code charset}. All parameters must be
     * effectively immutable, via safe copies, not mutating or otherwise.
     */
    public static Request create(String method, String url, Map<String, Collection<String>> headers, byte[] body) {
        return new Request(method, url, headers, body);
    }

    private final String method;
    private final String url;
    private final Map<String, Collection<String>> headers;
    private final byte[] body;

    Request(String method, String url, Map<String, Collection<String>> headers, byte[] body) {
        this.method = checkNotNull(method, "method of %s", url);
        this.url = checkNotNull(url, "url");
        this.headers = checkNotNull(headers, "headers of %s %s", method, url);
        this.body = body; // nullable
    }

    /** Method to invoke on the server. */
    public String method() {
        return method;
    }

    /** Fully resolved URL including query. */
    public String url() {
        return url;
    }

    /** Ordered list of headers that will be sent to the server. */
    public Map<String, Collection<String>> headers() {
        return headers;
    }

    /**
     * If present, this is the replayable body to send to the server.  In some cases, this may be
     * interpretable as text.
     */
    public byte[] body() {
        return body;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(method).append(' ').append(url).append(" HTTP/1.1\n");
        for (String field : headers.keySet()) {
            for (String value : valuesOrEmpty(headers, field)) {
                builder.append(field).append(": ").append(value).append('\n');
            }
        }
        if (body != null) {
            builder.append('\n').append(new String(body, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }
}
