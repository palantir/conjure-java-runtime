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

package com.palantir.conjure.java.server.jersey;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

abstract class ListenableExceptionMapper<T extends Throwable> implements ExceptionMapper<T> {

    private final ConjureJerseyFeature.ExceptionListener listener;

    ListenableExceptionMapper(ConjureJerseyFeature.ExceptionListener listener) {
        this.listener = listener;
    }

    /** Just like the jaxrs {@link javax.ws.rs.ext.ExceptionMapper#toResponse} method. */
    abstract Response toResponseInner(T exception);

    @Override
    public final Response toResponse(T exception) {
        try {
            listener.onException(exception);
            return toResponseInner(exception);
        } finally {
            listener.afterResponseBuilt();
        }
    }
}
