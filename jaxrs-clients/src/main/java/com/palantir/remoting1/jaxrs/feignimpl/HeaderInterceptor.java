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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HeaderInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HeaderInterceptor.class);

    private final Map<String, String> headers;

    private HeaderInterceptor(Map<String, String> headers) {
        this.headers = Preconditions.checkNotNull(ImmutableMap.copyOf(headers),
                "Custom request headers cannot be null");
    }

    public static HeaderInterceptor of(Map<String, String> headers) {
        return new HeaderInterceptor(headers);
    }

    @Override
    public void apply(RequestTemplate template) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (template.headers().containsKey(header.getKey())) {
                log.warn("Header {} with value {} is being overwritten with value {}", header.getKey(),
                        template.headers().get(header.getKey()), header.getValue());
            }
            template.header(header.getKey(), header.getValue());
        }
    }
}
