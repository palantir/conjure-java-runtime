/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting1.servers.jersey;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.ws.rs.ext.ExceptionMapper;

public final class ExceptionMappers {

    public enum StacktracePropagation {
        /**
         * The inverse of {@link #DO_NOT_PROPAGATE}. Note that this may leak sensitive information from servers to
         * clients.
         */
        PROPAGATE,

        /**
         * Configures exception serializers to not include exception stacktraces in {@link
         * com.palantir.remoting1.errors.SerializableError serialized errors}. This is the recommended setting.
         */
        DO_NOT_PROPAGATE
    }

    private ExceptionMappers() {}

    public static List<ExceptionMapper<? extends Exception>> getExceptionMappers(
            StacktracePropagation stacktracePropagation) {
        boolean includeStackTrace = stacktracePropagation == StacktracePropagation.PROPAGATE;
        return ImmutableList.of(
                new IllegalArgumentExceptionMapper(includeStackTrace),
                new NoContentExceptionMapper(),
                new RuntimeExceptionMapper(includeStackTrace),
                new WebApplicationExceptionMapper(includeStackTrace),
                new RemoteExceptionMapper());
    }
}
