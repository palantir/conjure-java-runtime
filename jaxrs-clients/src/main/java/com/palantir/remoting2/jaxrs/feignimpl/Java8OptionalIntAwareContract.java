/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.jaxrs.feignimpl;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import feign.Contract;
import feign.MethodMetadata;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalInt;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

/**
 * Decorates a {@link Contract} and uses {@link Java8NullOptionalIntExpander} for any {@link QueryParam} parameters
 * and throws a {@link RuntimeException} at first encounter of an {@link Optional} typed {@link PathParam}
 * or {@link HeaderParam}.
 * <p>
 * {@link PathParam}s require a value, and so we explicitly disallow use with {@link Optional}.
 */
public final class Java8OptionalIntAwareContract extends AbstractDelegatingContract {

    public Java8OptionalIntAwareContract(Contract delegate) {
        super(delegate);
    }

    @Override
    protected void processMetadata(Class<?> targetType, Method method, MethodMetadata metadata) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Annotation[][] annotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> cls = parameterTypes[i];
            if (cls.equals(OptionalInt.class)) {
                FluentIterable<Class<?>> paramAnnotations =
                        FluentIterable.from(Lists.newArrayList(annotations[i])).transform(EXTRACT_CLASS);
                if (paramAnnotations.contains(QueryParam.class)) {
                    metadata.indexToExpanderClass().put(i, Java8NullOptionalIntExpander.class);
                } else if (paramAnnotations.contains(HeaderParam.class)) {
                    throw new RuntimeException(String.format(
                            "Cannot use Java8 OptionalInt with HeaderParams. (Class: %s, Method: %s, Param: arg%d)",
                            targetType.getName(), method.getName(), i));
                } else if (paramAnnotations.contains(PathParam.class)) {
                    throw new RuntimeException(String.format(
                            "Cannot use Java8 OptionalInt with PathParams. (Class: %s, Method: %s, Param: arg%d)",
                            targetType.getName(), method.getName(), i));
                }
            }
        }
    }

    private static final Function<Annotation, Class<?>> EXTRACT_CLASS = input -> input.annotationType();
}
