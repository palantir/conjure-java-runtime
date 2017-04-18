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

public final class CompressionFilter implements ContainerResponseFilter {

    // List of encodings ordered by compression preference
    private static final List<Encoding> ENCODINGS = ImmutableList.of(
            new Encoding("deflate", wrap(DeflaterOutputStream::new)),
            new Encoding("gzip", wrap(GZIPOutputStream::new)));

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        if (!alreadyEncoded(responseContext)) {
            for (Encoding encoding : ENCODINGS) {
                if (acceptsEncoding(requestContext, encoding.encoding)) {
                    MultivaluedMap<String, Object> headers = responseContext.getHeaders();

                    // Set 'Vary: Accept-Encoding' so intermediate caches differentiate between differently
                    // encoded responses
                    headers.add(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
                    headers.add(HttpHeaders.CONTENT_ENCODING, encoding.encoding);
                    responseContext.setEntityStream(encoding.compressor.apply(responseContext.getEntityStream()));
                }
            }
        }
    }

    private static boolean alreadyEncoded(ContainerResponseContext response) {
        List<Object> encodings = response.getHeaders().get(HttpHeaders.CONTENT_ENCODING);
        return encodings != null && encodings.size() > 0;
    }

    private static boolean acceptsEncoding(ContainerRequestContext request, String encoding) {
        List<String> encodings = request.getHeaders().get(HttpHeaders.ACCEPT_ENCODING);
        return encodings != null && encodings.contains(encoding);
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

    interface IOFunction<T, R> {
        R apply(T val) throws IOException;
    }

    static class Encoding {
        private final String encoding;
        private final Function<OutputStream, OutputStream> compressor;

        Encoding(String encoding, Function<OutputStream, OutputStream> compressor) {
            this.encoding = encoding;
            this.compressor = compressor;
        }
    }

}
