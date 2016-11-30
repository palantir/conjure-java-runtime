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

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.Collections;
import java.util.Map;

public final class CustomizableInterceptor implements RequestInterceptor {

    private final Map<String, String> headers;

    private CustomizableInterceptor(Map<String, String> headers) {
        this.headers = (headers != null) ? headers : Collections.<String, String>emptyMap();
    }

    public static CustomizableInterceptor of(Map<String, String> headers) {
        return new CustomizableInterceptor(headers);
    }

    @Override
    public void apply(RequestTemplate template) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            template.header(header.getKey(), header.getValue());
        }
    }
}
