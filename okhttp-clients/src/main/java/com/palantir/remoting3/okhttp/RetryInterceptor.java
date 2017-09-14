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

import com.palantir.logsafe.SafeArg;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrofit {@link Interceptor} that retries at most {@code maxNumTries} times.
 */
public final class RetryInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(RetryInterceptor.class);
    private static final Integer DEFAULT_MAX_NUM_RETRIES = 3;

    private final long maxNumTries;

    public RetryInterceptor() {
        this.maxNumTries = DEFAULT_MAX_NUM_RETRIES;
    }

    public RetryInterceptor(long maxNumTries) {
        this.maxNumTries = maxNumTries;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        long numTries = 0;
        boolean successful = false;
        Request request = chain.request();
        Response response = null;

        while (!successful && numTries < maxNumTries) {
            try {
                numTries++;
                response = chain.proceed(request);
                successful = response.isSuccessful();
            } catch (IOException e) {
                // Ignore and retry unless the request has already been tried at least the max number of times
                if (numTries >= maxNumTries) {
                    throw e;
                }
            }
            if (!successful) {
                log.error("Request to url {} failed on attempt {}", request.url(), SafeArg.of("numTries", numTries));
            }
        }

        return response;
    }
}
