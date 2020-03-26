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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import com.palantir.dialogue.HttpMethod;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeNullPointerException;
import feign.Contract;
import feign.MethodMetadata;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Contract to capture the endpoint name (method name) and pass it to a feign client.
 *
 * This should be considered internal API and should not be depended upon.
 */
public final class EndpointNameHeaderEnrichmentContract extends AbstractDelegatingContract {

    public static final String ENDPOINT_NAME_HEADER = "dialogue-endpoint-name";

    public EndpointNameHeaderEnrichmentContract(Contract delegate) {
        super(delegate);
    }

    @Override
    protected void processMetadata(Class<?> targetType, Method method, MethodMetadata metadata) {
        String httpMethod = metadata.template().method();
        if (httpMethod == null) {
            throw new SafeNullPointerException(
                    "An HTTP method is required",
                    SafeArg.of("class", targetType.getSimpleName()),
                    SafeArg.of("method", method.getName()));
        }
        try {
            HttpMethod.valueOf(httpMethod.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new SafeIllegalArgumentException(
                    "Unsupported HTTP method",
                    SafeArg.of("class", targetType.getSimpleName()),
                    SafeArg.of("method", method.getName()),
                    SafeArg.of("httpMethod", httpMethod));
        }
        metadata.template().header(ENDPOINT_NAME_HEADER, method.getName());
    }
}
