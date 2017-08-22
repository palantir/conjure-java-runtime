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

package com.palantir.remoting3.okhttp;

import okhttp3.Interceptor;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** An OkHttp {@link Interceptor}-based logger for suitable for logging OkHttp internals through Slf4j. */
public final class OkhttpSlf4jDebugLogger implements HttpLoggingInterceptor.Logger {

    public static final OkhttpSlf4jDebugLogger INSTANCE = new OkhttpSlf4jDebugLogger();
    private static final Logger log = LoggerFactory.getLogger(OkhttpSlf4jDebugLogger.class);

    private OkhttpSlf4jDebugLogger() {}

    @Override
    public void log(String message) {
        log.debug(message);
    }
}
