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

package com.palantir.remoting.http;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.regex.Pattern;

public final class UserAgentInterceptor implements RequestInterceptor {

    private static final Pattern VALID_USER_AGENT = Pattern.compile("[A-Za-z0-9/\\.,_\\s]+");
    private final String userAgent;

    public UserAgentInterceptor(String userAgent) {
        if (!VALID_USER_AGENT.matcher(userAgent).matches()) {
            throw new IllegalArgumentException("User Agent " + userAgent + " is not valid.");
        }
        this.userAgent = userAgent;
    }

    public static UserAgentInterceptor of(String userAgent) {
        return new UserAgentInterceptor(userAgent);
    }

    @Override
    public void apply(RequestTemplate template) {
        if (!template.headers().containsKey("User-Agent")) {
            template.header("User-Agent", userAgent);
        }
    }
}
