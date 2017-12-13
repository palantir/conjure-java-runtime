/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.remoting3.jaxrs.feignimpl;

import com.palantir.remoting3.tracing.okhttp3.OkhttpTraceInterceptor;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public enum PathTemplateHeaderRewriter implements RequestInterceptor {
    INSTANCE;

    @Override
    public void apply(RequestTemplate template) {
        if (template.headers().containsKey(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER)) {
            Collection<String> rewrittenHeaders = template.headers().get(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER)
                    .stream()
                    .map(headerValue ->
                            headerValue.replace(PathTemplateHeaderEnrichmentContract.OPEN_BRACE_REPLACEMENT, '{')
                                    .replace(PathTemplateHeaderEnrichmentContract.CLOSE_BRACE_REPLACEMENT, '}'))
                    .collect(Collectors.toList());
            Map<String, Collection<String>> headers = new HashMap<>(template.headers());
            headers.put(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER, rewrittenHeaders);
            template.headers(headers);
        }
    }
}
