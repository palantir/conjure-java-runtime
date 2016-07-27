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

package com.palantir.remoting.jaxrs.feignimpl;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.net.HttpHeaders;
import feign.RequestTemplate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public final class UserAgentInterceptorTest {
    private static final UserAgentInterceptor INTERCEPTOR = UserAgentInterceptor.of("Test");

    @Test
    public void shouldAddUserAgentHeaderIfNotPresent() {
        RequestTemplate template = new RequestTemplate();
        INTERCEPTOR.apply(template);
        assertThat(template.headers(), is(singletonHeaderMap(HttpHeaders.USER_AGENT, "Test")));
    }

    @Test
    public void shouldNotChangeUserAgentHeaderIfAlreadyPresent() {
        RequestTemplate template = new RequestTemplate().header(HttpHeaders.USER_AGENT, "Already present");
        INTERCEPTOR.apply(template);
        assertThat(template.headers(), is(singletonHeaderMap(HttpHeaders.USER_AGENT, "Already present")));
    }

    @Test
    public void shouldNotChangeUserAgentHeaderIfAlreadyPresentWithArbitraryHeaderNameCapitalization() {
        RequestTemplate template = new RequestTemplate().header("USER-agent", "Already present");
        INTERCEPTOR.apply(template);
        assertThat(template.headers(), is(singletonHeaderMap("USER-agent", "Already present")));
    }

    private static Map<String, Collection<String>> singletonHeaderMap(String key, String value) {
        return Collections.<String, Collection<String>>singletonMap(key, Arrays.asList(value));
    }
}
