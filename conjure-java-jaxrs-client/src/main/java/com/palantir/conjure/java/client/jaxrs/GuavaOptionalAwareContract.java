/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.jaxrs;

import com.palantir.conjure.java.client.jaxrs.JaxRsJakartaCompatibility.Annotations;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decorates a {@link Contract} and uses {@link GuavaNullOptionalExpander} for
 * any {@link jakarta.ws.rs.QueryParam} parameters,
 * {@link GuavaEmptyOptionalExpander} for any {@link jakarta.ws.rs.HeaderParam} parameters,
 * and throws a {@link RuntimeException} at
 * first encounter of an {@link com.google.common.base.Optional} typed {@link jakarta.ws.rs.PathParam}.
 *
 * <p>{@link jakarta.ws.rs.PathParam}s require a value, and so we explicitly disallow use with
 * {@link com.google.common.base.Optional}.
 */
final class GuavaOptionalAwareContract extends AbstractDelegatingContract {

    GuavaOptionalAwareContract(Contract delegate) {
        super(delegate);
    }

    @Override
    protected void processMetadata(Class<?> targetType, MethodMetadata metadata) {
        Class<?>[] parameterTypes = metadata.method().getParameterTypes();
        Annotation[][] annotations = metadata.method().getParameterAnnotations();
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> cls = parameterTypes[i];
            if (cls.equals(com.google.common.base.Optional.class)) {
                Set<Class<?>> paramAnnotations = Arrays.stream(annotations[i])
                        .map(Annotation::annotationType)
                        .collect(Collectors.toSet());
                if (Annotations.HEADER_PARAM.matches(paramAnnotations)) {
                    metadata.indexToExpanderClass().put(i, GuavaEmptyOptionalExpander.class);
                } else if (Annotations.QUERY_PARAM.matches(paramAnnotations)) {
                    metadata.indexToExpanderClass().put(i, GuavaNullOptionalExpander.class);
                } else if (Annotations.PATH_PARAM.matches(paramAnnotations)) {
                    throw new RuntimeException(String.format(
                            "Cannot use Guava Optionals with PathParams. (Class: %s, Method: %s, Param: arg%d)",
                            targetType.getName(), metadata.method().getName(), i));
                }
            }
        }
    }
}
