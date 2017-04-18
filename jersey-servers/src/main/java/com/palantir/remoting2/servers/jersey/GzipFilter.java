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

import com.google.common.net.HttpHeaders;
import com.jcraft.jzlib.GZIPOutputStream;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

public final class GzipFilter implements ContainerResponseFilter {

    private static final String GZIP = "gzip";

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        if (acceptsGzipEncoding(requestContext) && !alreadyEncoded(responseContext)) {
            responseContext.getHeaders().add(HttpHeaders.CONTENT_ENCODING, GZIP);
            responseContext.setEntityStream(new GZIPOutputStream(responseContext.getEntityStream()));
        }
    }

    private static boolean alreadyEncoded(ContainerResponseContext response) {
        List<Object> encodings = response.getHeaders().get(HttpHeaders.CONTENT_ENCODING);
        return encodings != null && encodings.size() > 0;
    }

    private static boolean acceptsGzipEncoding(ContainerRequestContext request) {
        List<String> encodings = request.getHeaders().get(HttpHeaders.ACCEPT_ENCODING);
        return encodings != null && encodings.contains(GZIP);
    }

}
