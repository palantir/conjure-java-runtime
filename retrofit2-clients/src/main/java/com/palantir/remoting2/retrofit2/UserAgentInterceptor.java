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

package com.palantir.remoting2.retrofit2;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.util.regex.Pattern;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class UserAgentInterceptor implements Interceptor {

    // keep in sync with corresponding pattern in UserAgentInterceptor.java in jaxrs-clients project
    private static final Pattern VALID_USER_AGENT = Pattern.compile("[A-Za-z0-9()\\-#;/.,_\\s]+");
    private final String userAgent;

    private UserAgentInterceptor(String userAgent) {
        checkArgument(VALID_USER_AGENT.matcher(userAgent).matches(),
                "User Agent must match pattern '%s': %s", VALID_USER_AGENT, userAgent);
        this.userAgent = userAgent;
    }

    public static UserAgentInterceptor of(String userAgent) {
        return new UserAgentInterceptor(userAgent);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        Request requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", userAgent)
                .build();
        return chain.proceed(requestWithUserAgent);
    }
}
