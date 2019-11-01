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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import feign.Contract;
import feign.MethodMetadata;
import feign.Param.Expander;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

/**
 * Decorates a {@link Contract} and uses {@link Java8NullOptionalExpander} for any {@link QueryParam} parameters, {@link
 * Java8EmptyOptionalExpander} for any {@link HeaderParam} parameters, and throws a {@link RuntimeException} at first
 * encounter of an {@link Optional} typed {@link PathParam}.
 *
 * <p>{@link PathParam}s require a value, and so we explicitly disallow use with {@link Optional}.
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
                    FluentIterable<Class<?>> paramAnnotations = getAnnotations(annotations, i);
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

    private FluentIterable<Class<?>> getAnnotations(Annotation[][] annotations, int index) {
        return FluentIterable.from(Lists.newArrayList(annotations[index])).transform(EXTRACT_CLASS);
    }

    private void configureOptionalExpanders(
            Class<?> targetType,
            Method method,
            MethodMetadata metadata,
            int index,
            FluentIterable<Class<?>> paramAnnotations,
            Class<? extends Expander> emptyExpanderClass,
            Class<? extends Expander> nullExpanderClass) {
        if (paramAnnotations.contains(HeaderParam.class)) {
            metadata.indexToExpanderClass().put(index, emptyExpanderClass);
        } else if (paramAnnotations.contains(QueryParam.class)) {
            metadata.indexToExpanderClass().put(index, nullExpanderClass);
        } else if (paramAnnotations.contains(PathParam.class)) {
            throw new RuntimeException(String.format(
                    "Cannot use Java8 Optionals with PathParams. (Class: %s, Method: %s, Param: arg%d)",
                    targetType.getName(), method.getName(), index));
        }
    }

    private static final Function<Annotation, Class<?>> EXTRACT_CLASS = input -> input.annotationType();

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
