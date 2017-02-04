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

import feign.Contract;
import feign.MethodMetadata;
import feign.Param;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

/**
 * Decorates a {@link Contract} and uses {@link Java8NullOptionalExpander} for any {@link QueryParam} parameters,
 * {@link Java8EmptyOptionalExpander} for any {@link HeaderParam} parameters, and throws a {@link RuntimeException}
 * at first encounter of an {@link Optional} typed {@link PathParam}.
 * <p>
 * {@link PathParam}s require a value, and so we explicitly disallow use with {@link Optional}.
 */
public final class Java8OptionalAwareContract extends AbstractDelegatingContract {

    public Java8OptionalAwareContract(Contract delegate) {
        super(delegate);
    }

    @Override
    protected void processMetadata(Class<?> targetType, Method method, MethodMetadata metadata) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Annotation[][] annotations = method.getParameterAnnotations();

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> cls = parameterTypes[i];
            Collection<Class<?>> paramAnnotations = Arrays.stream(annotations[i])
                    .map(Annotation::annotationType)
                    .collect(Collectors.toSet());

            int copyOfI = i;
            CannotUseExceptionFactory cannotUseExceptionFactory = (paramType) -> new RuntimeException(String.format(
                    "Cannot use Java8 optional type with %s. (Class: %s, Method: %s, Param: arg%d)",
                    paramType, targetType.getName(), method.getName(), copyOfI
            ));

            expanderFactoryFor(cls, cannotUseExceptionFactory)
                    .flatMap(expanderFactory -> expanderFactory.expanderFor(paramAnnotations))
                    .ifPresent((expander) -> metadata.indexToExpanderClass().put(copyOfI, expander));
        }
    }

    private interface CannotUseExceptionFactory {
        RuntimeException forType(String type);
    }

    private abstract static class ExpanderFactory {
        private final CannotUseExceptionFactory cannotUseExceptionFactory;

        ExpanderFactory(CannotUseExceptionFactory cannotUseExceptionFactory) {
            this.cannotUseExceptionFactory = cannotUseExceptionFactory;
        }

        public Class<? extends Param.Expander> headerExpander() {
            throw cannotUseExceptionFactory.forType("HeaderParams");
        }

        public Class<? extends Param.Expander> pathExpander() {
            throw cannotUseExceptionFactory.forType("PathParams");
        }

        public Class<? extends Param.Expander> queryExpander() {
            return Java8NullOptionalExpander.class;
        }

        public final Optional<Class<? extends Param.Expander>> expanderFor(Collection<Class<?>> paramAnnotations) {
            if (paramAnnotations.contains(HeaderParam.class)) {
                return Optional.of(headerExpander());
            }

            if (paramAnnotations.contains(QueryParam.class)) {
                return Optional.of(queryExpander());
            }

            if (paramAnnotations.contains(PathParam.class)) {
                return Optional.of(pathExpander());
            }

            return Optional.empty();
        }
    }

    private static class OptionalExpanderFactory extends ExpanderFactory {
        OptionalExpanderFactory(CannotUseExceptionFactory cannotUseExceptionFactory) {
            super(cannotUseExceptionFactory);
        }

        @Override
        public Class<? extends Param.Expander> headerExpander() {
            return Java8EmptyOptionalExpander.class;
        }
    }

    private static class OptionalIntExpanderFactory extends ExpanderFactory {
        OptionalIntExpanderFactory(CannotUseExceptionFactory cannotUseExceptionFactory) {
            super(cannotUseExceptionFactory);
        }
    }

    private Optional<ExpanderFactory> expanderFactoryFor(Class<?> type, CannotUseExceptionFactory cannotUseExceptionFactory) {
        if (type.equals(Optional.class)) {
            return Optional.of(new OptionalExpanderFactory(cannotUseExceptionFactory));
        }

        if (type.equals(OptionalInt.class)) {
            return Optional.of(new OptionalIntExpanderFactory(cannotUseExceptionFactory));
        }

        return Optional.empty();
    }
}
