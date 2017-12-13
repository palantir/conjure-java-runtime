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

package com.palantir.remoting3.servers.jersey;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
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
 * <p>
 * This is an alternative compression filter implementation that uses jzlib for compression rather than the java
 * standard library due to performance issues observed on high core machines:
 * https://bugs.openjdk.java.net/browse/JDK-8179001
 */
public final class CompressionFilter implements ContainerResponseFilter {

    private static final ImmutableSet<MediaType> UNCOMPRESSIBLE = ImmutableSet.of(
            MediaType.ANY_AUDIO_TYPE,
            MediaType.ANY_IMAGE_TYPE,
            MediaType.ANY_VIDEO_TYPE);

    private final LoadingCache<List<String>, List<String>> parsedHeaders;
    private final int minCompressionBytes;

    public CompressionFilter() {
        this(0);
    }

    public CompressionFilter(int minCompressionBytes) {
        this.minCompressionBytes = minCompressionBytes;
        this.parsedHeaders = CacheBuilder.newBuilder()
                .maximumSize(100)
                .build(new CacheLoader<List<String>, List<String>>() {
                    @Override
                    public List<String> load(List<String> headerLines) throws Exception {
                        return acceptedEncodings(headerLines);
                    }
                });
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        if (isTooSmall(responseContext) || isEncoded(responseContext) || isUncompressable(responseContext)) {
            return;
        }

        List<String> headerLines = requestContext.getHeaders().get(HttpHeaders.ACCEPT_ENCODING);
        if (headerLines == null) {
            return;
        }

        List<String> acceptedEncodings = parsedHeaders.getUnchecked(headerLines);
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

    private boolean isTooSmall(ContainerResponseContext response) {
        int length = response.getLength();
        return length != -1 && length < minCompressionBytes;
    }

    private static boolean isEncoded(ContainerResponseContext response) {
        List<Object> encodings = response.getHeaders().get(HttpHeaders.CONTENT_ENCODING);
        return encodings != null && encodings.size() > 0;
    }

    private static boolean isUncompressable(ContainerResponseContext response) {
        MediaType mediaType = MediaType.parse(response.getMediaType().toString());
        return UNCOMPRESSIBLE.stream().anyMatch(mediaType::is);
    }

    private static List<String> acceptedEncodings(List<String> headerLines) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();

        // Single line looks like `deflate, gzip;q=1.0, *;q=0.5`
        // See section 14.3: https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
        // TODO(jellis): don't ignore qvalues
        for (String line : headerLines) {
            for (String acceptedEncoding : line.split(",")) {
                String rawEncoding = acceptedEncoding.split(";")[0];
                builder.add(rawEncoding.trim());
            }
        }

        return builder.build();
    }

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
