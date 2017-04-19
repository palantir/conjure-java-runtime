/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.servers.jersey;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.jcraft.jzlib.DeflaterOutputStream;
import com.jcraft.jzlib.GZIPOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Function;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;

/**
 * Compresses responses based on the Accept-Encoding header in the request.
 */
public final class CompressionFilter implements ContainerResponseFilter {

    /**
     * Supported encodings. Prefer gzip over deflate as Jetty does.
     * TODO(jellis): support qvalues in Accept-Encoding header params to determine preferred ordering.
     */
    enum Encoding {
        GZIP("gzip", wrap(GZIPOutputStream::new)),
        DEFLATE("deflate", wrap(DeflaterOutputStream::new));

        private final String encoding;
        private final Function<OutputStream, OutputStream> compressor;

        Encoding(String encoding, Function<OutputStream, OutputStream> compressor) {
            this.encoding = encoding;
            this.compressor = compressor;
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        if (alreadyEncoded(responseContext)) {
            return;
        }

        List<String> acceptedEncodings = acceptedEncodings(requestContext);

        for (Encoding encoding : Encoding.values()) {
            if (acceptedEncodings.contains(encoding.encoding)) {
                MultivaluedMap<String, Object> headers = responseContext.getHeaders();

                // Set 'Vary: Accept-Encoding' so intermediate caches differentiate between differently
                // encoded responses
                headers.add(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
                headers.add(HttpHeaders.CONTENT_ENCODING, encoding.encoding);
                responseContext.setEntityStream(encoding.compressor.apply(responseContext.getEntityStream()));
                return;
            }
        }
    }

    private static boolean alreadyEncoded(ContainerResponseContext response) {
        List<Object> encodings = response.getHeaders().get(HttpHeaders.CONTENT_ENCODING);
        return encodings != null && encodings.size() > 0;
    }

    private static List<String> acceptedEncodings(ContainerRequestContext request) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        List<String> headerLines = request.getHeaders().get(HttpHeaders.ACCEPT_ENCODING);

        if (headerLines == null) {
            return builder.build();
        }

        // Single line looks like `deflate, gzip;q=1.0, *;q=0.5`
        // See section 14.3: https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
        // TODO(jellis): don't ignore qvalues
        for (String line : headerLines) {
            for (String acceptedEncoding : line.split(",")) {
                builder.add(acceptedEncoding.split(";")[0]);
            }
        }

        return builder.build();
    }

    // Wrap functions that throw IOExceptions
    private static <T, R> Function<T, R> wrap(IOFunction<T, R> func) {
        return val -> {
            try {
                return func.apply(val);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @SuppressWarnings("AbbreviationAsWordInName")
    interface IOFunction<T, R> {
        R apply(T val) throws IOException;
    }

}
