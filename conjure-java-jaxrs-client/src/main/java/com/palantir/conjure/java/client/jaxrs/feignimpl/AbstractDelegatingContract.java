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

import feign.Contract;
import feign.Feign;
import feign.MethodMetadata;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class that provides the structure for a delegating {@link Contract}. Delegates the initial {@link
 * #parseAndValidatateMetadata(Class)} call to the wrapped Contract and then calls {@link #processMetadata(Class,
 * Method, MethodMetadata)} on all of the methods that have metadata from the initial call.
 */
abstract class AbstractDelegatingContract implements Contract {

    private final Contract delegate;

    AbstractDelegatingContract(Contract delegate) {
        this.delegate = delegate;
    }

    @Override
    public final List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
        List<MethodMetadata> mdList = delegate.parseAndValidatateMetadata(targetType);

        Map<String, MethodMetadata> methodMetadataByConfigKey = new LinkedHashMap<String, MethodMetadata>();
        for (MethodMetadata md : mdList) {
            methodMetadataByConfigKey.put(md.configKey(), md);
        }

        for (Method method : targetType.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            String configKey = Feign.configKey(targetType, method);
            MethodMetadata metadata = methodMetadataByConfigKey.get(configKey);
            if (metadata != null) {
                processMetadata(targetType, method, metadata);
            }
        }

        return mdList;
    }

    protected abstract void processMetadata(Class<?> targetType, Method method, MethodMetadata metadata);
}
