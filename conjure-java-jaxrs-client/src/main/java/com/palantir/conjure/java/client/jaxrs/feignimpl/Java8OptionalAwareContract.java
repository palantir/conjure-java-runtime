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

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.client.jaxrs.JaxRsJakartaCompatibility.Annotations;
import feign.Contract;
import feign.MethodMetadata;
import feign.Param.Expander;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decorates a {@link Contract} and uses {@link Java8NullOptionalExpander}
 * for any {@link jakarta.ws.rs.QueryParam} parameters,
 * {@link Java8EmptyOptionalExpander} for any {@link jakarta.ws.rs.HeaderParam} parameters,
 * and throws a {@link RuntimeException} at
 * first encounter of an {@link Optional} typed {@link jakarta.ws.rs.PathParam}.
 *
 * <p>{@link jakarta.ws.rs.PathParam}s require a value, and so we explicitly disallow use with {@link Optional}.
 */
public final class Java8OptionalAwareContract extends AbstractDelegatingContract {

    private static final List<ExpanderDef> expanders = ImmutableList.of(
            new ExpanderDef(Optional.class, Java8EmptyOptionalExpander.class, Java8NullOptionalExpander.class),
            new ExpanderDef(OptionalInt.class, Java8EmptyOptionalIntExpander.class, Java8NullOptionalIntExpander.class),
            new ExpanderDef(
                    OptionalDouble.class,
                    Java8EmptyOptionalDoubleExpander.class,
                    Java8NullOptionalDoubleExpander.class),
            new ExpanderDef(
                    OptionalLong.class, Java8EmptyOptionalLongExpander.class, Java8NullOptionalLongExpander.class));

    public Java8OptionalAwareContract(Contract delegate) {
        super(delegate);
    }

    @Override
    protected void processMetadata(Class<?> targetType, Method method, MethodMetadata metadata) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Annotation[][] annotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> cls = parameterTypes[i];
            for (ExpanderDef def : expanders) {
                if (cls.equals(def.match)) {
                    Set<Class<?>> paramAnnotations = getAnnotations(annotations, i);
                    configureOptionalExpanders(
                            targetType,
                            method,
                            metadata,
                            i,
                            paramAnnotations,
                            def.emptyExpanderClass,
                            def.nullExpanderClass);
                }
            }
        }
    }

    private Set<Class<?>> getAnnotations(Annotation[][] annotations, int index) {
        return Arrays.stream(annotations[index]).map(Annotation::annotationType).collect(Collectors.toSet());
    }

    private void configureOptionalExpanders(
            Class<?> targetType,
            Method method,
            MethodMetadata metadata,
            int index,
            Set<Class<?>> paramAnnotations,
            Class<? extends Expander> emptyExpanderClass,
            Class<? extends Expander> nullExpanderClass) {
        if (Annotations.HEADER_PARAM.matches(paramAnnotations)) {
            metadata.indexToExpanderClass().put(index, emptyExpanderClass);
        } else if (Annotations.QUERY_PARAM.matches(paramAnnotations)) {
            metadata.indexToExpanderClass().put(index, nullExpanderClass);
        } else if (Annotations.PATH_PARAM.matches(paramAnnotations)) {
            throw new RuntimeException(String.format(
                    "Cannot use Java8 Optionals with PathParams. (Class: %s, Method: %s, Param: arg%d)",
                    targetType.getName(), method.getName(), index));
        }
    }

    private static final class ExpanderDef {
        private final Class<?> match;
        private final Class<? extends Expander> emptyExpanderClass;
        private final Class<? extends Expander> nullExpanderClass;

        ExpanderDef(
                Class<?> match,
                Class<? extends Expander> emptyExpanderClass,
                Class<? extends Expander> nullExpanderClass) {
            this.match = match;
            this.emptyExpanderClass = emptyExpanderClass;
            this.nullExpanderClass = nullExpanderClass;
        }
    }
}
