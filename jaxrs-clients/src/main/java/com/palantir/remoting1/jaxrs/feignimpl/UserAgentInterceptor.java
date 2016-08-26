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

package com.palantir.remoting1.jaxrs.feignimpl;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.net.HttpHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.regex.Pattern;

public final class UserAgentInterceptor implements RequestInterceptor {

    // keep in sync with corresponding pattern in UserAgentInterceptor.java in retrofit-clients project
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
    public void apply(RequestTemplate template) {
        if (!HeaderAccessUtils.caseInsensitiveContains(template.headers(), HttpHeaders.USER_AGENT)) {
            template.header(HttpHeaders.USER_AGENT, userAgent);
        }
    }
}
