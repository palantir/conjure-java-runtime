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

import com.google.common.base.Optional;
import feign.Contract;
import feign.Feign;
import feign.MethodMetadata;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decorates a Contract and uses {@link GuavaOptionalExpander} for any HeaderParam, PathParam or QueryParam parameters.
 */
public final class GuavaOptionalAwareContract implements Contract {

    private final Contract delegate;

    public GuavaOptionalAwareContract(Contract delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
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
            MethodMetadata md = methodMetadataByConfigKey.get(configKey);
            if (md != null) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> cls = parameterTypes[i];
                    if (cls.equals(Optional.class)) {
                        md.indexToExpanderClass().put(i, GuavaOptionalExpander.class);
                    }
                }
            }
        }
        return mdList;
    }

}
