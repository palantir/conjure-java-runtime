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

package com.palantir.remoting3.jaxrs;

import com.palantir.remoting3.okhttp.RemoteIoException;
import feign.Client;
import feign.Request;
import feign.Response;
import java.io.IOException;

/**
 * This client's sole purpose is to turn our special {@link RemoteIoException} into a RuntimeException.
 * <p>
 * It is necessary because okhttp only propagates IOExceptions nicely through all its async futures {@see
 * okhttp3.RealCall}, but unfortunately feign turns IOExceptions into ugly FeignExceptions in its {@link
 * feign.SynchronousMethodHandler}.
 * <p>
 * Similar to {@link com.palantir.remoting3.okhttp.QosIoException}.
 */
public final class RemoteIoExceptionClient implements Client {
    private final Client delegate;

    public RemoteIoExceptionClient(Client delegate) {
        this.delegate = delegate;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        try {
            return delegate.execute(request, options);
        } catch (RemoteIoException e) {
            throw e.getRuntimeExceptionCause();
        }
    }
}
