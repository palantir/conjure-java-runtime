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

package com.palantir.remoting1.servers;

import javax.ws.rs.ext.ExceptionMapper;

final class ExceptionMappers {

    /** Java7-compatible version of Java8 Consumer. */
    interface Consumer<T> {
        void accept(T object);
    }

    private ExceptionMappers() {}

    static void visitExceptionMappers(boolean includeStackTrace,
            Consumer<ExceptionMapper<? extends Throwable>> consumer) {
        consumer.accept(new IllegalArgumentExceptionMapper(includeStackTrace));
        consumer.accept(new NoContentExceptionMapper());
        consumer.accept(new RuntimeExceptionMapper(includeStackTrace));
        consumer.accept(new WebApplicationExceptionMapper(includeStackTrace));
        consumer.accept(new RemoteExceptionMapper(includeStackTrace));
    }
}
