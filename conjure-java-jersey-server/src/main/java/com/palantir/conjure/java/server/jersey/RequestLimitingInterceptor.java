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

package com.palantir.conjure.java.server.jersey;

import com.google.common.annotations.VisibleForTesting;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting.api.errors.ErrorType;
import com.palantir.remoting.api.errors.ServiceException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.ReaderInterceptorContext;

/**
 * Limit requests to 50MB unless the endpoint streams the input.
 */
public final class RequestLimitingInterceptor implements ReaderInterceptor {

    private static final int MAX_REQUEST_SIZE_BYTES = 50_000_000;

    private final int maxRequestSize;

    RequestLimitingInterceptor() {
        this(MAX_REQUEST_SIZE_BYTES);
    }

    @VisibleForTesting
    RequestLimitingInterceptor(int maxRequestSize) {
        this.maxRequestSize = maxRequestSize;
    }

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {
        LimitingInputStream limitedStream = new LimitingInputStream(context.getInputStream(), maxRequestSize);
        context.setInputStream(limitedStream);

        Object result = context.proceed();

        // If we have not thrown yet, the endpoint streams the input or it has been consumed, so do not limit
        limitedStream.doNotLimit();

        return result;
    }

    private static final class LimitingInputStream extends FilterInputStream {

        private final int maxRequestSize;

        private int bytesRead = 0;
        private boolean shouldLimit = true;

        LimitingInputStream(InputStream in, int maxRequestSize) {
            super(in);
            this.maxRequestSize = maxRequestSize;
        }

        void doNotLimit() {
            shouldLimit = false;
        }

        @Override
        public int read() throws IOException {
            int read = in.read();
            if (read != -1) {
                validateSize(1);
            }
            return read;
        }

        @Override
        public int read(byte[] bytes, int off, int len) throws IOException {
            int read = in.read(bytes, off, len);
            if (read != -1) {
                validateSize(read);
            }
            return read;
        }

        private void validateSize(int bytes) {
            bytesRead += bytes;
            if (bytesRead > maxRequestSize && shouldLimit) {
                throw new ServiceException(ErrorType.REQUEST_ENTITY_TOO_LARGE,
                        SafeArg.of("maxRequestSizeBytes", maxRequestSize));
            }
        }
    }
}
