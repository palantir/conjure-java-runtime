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

package com.palantir.conjure.java.okhttp;

import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * An {@link Interceptor} that changes the {@link okhttp3.Request#url Request URL's} authority to a given {@link
 * HostAndPort}, and adds an explicit "Host" header that is set to the original Request's authority. This allows the use
 * of a L4 service mesh proxy over SSL.
 */
final class MeshProxyInterceptor implements Interceptor {

    private final HostAndPort meshProxy;

    MeshProxyInterceptor(HostAndPort meshProxy) {
        this.meshProxy = meshProxy;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        HttpUrl originalUri = chain.request().url();

        // Not using URL#getAuthority since we are removing the userinfo portion.
        // Note that userinfo is not allowed in http2 :authority headers:
        // https://tools.ietf.org/html/rfc7540#section-8.1.2.3
        // or in the HTTP/1.1 Host header: https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.23
        StringBuilder host = new StringBuilder(originalUri.uri().getHost());
        if (originalUri.uri().getPort() != -1) {
            host.append(':');
            host.append(originalUri.uri().getPort());
        }

        return chain.proceed(
                chain.request()
                        .newBuilder()
                        .url(originalUri.newBuilder().host(meshProxy.getHost()).port(meshProxy.getPort()).build())
                        // TODO(jnewman): #612 - add HTTP2 :authority pseudo-header as well? Or does OkHttp handle this
                        // for us?
                        .header(HttpHeaders.HOST, host.toString())
                        .build());
    }
}
