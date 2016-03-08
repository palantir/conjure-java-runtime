/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http;

import com.google.common.base.Preconditions;
import feign.Contract;
import feign.MethodMetadata;
import feign.QueryMap;
import feign.jaxrs.JAXRSContract;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Decorates a {@link Contract} and records any instances of the {@link QueryMap} annotation
 * on method parameters. Useful in cases where the decorated {@link Contract} is not a
 * {@link Contract.Default} but the QueryMap functionality is still desired (for example, this can
 * wrap a {@link JAXRSContract} to add QueryMap annotation support).
 * <p>
 * NOTE: This decorator only enables QueryMaps to be used for methods that don't declare any
 * body parameters (typically GET methods). This is because if the wrapped Contract doesn't know
 * how to interpret the QueryMap parameter, it will likely interpret it as a body parameter, and
 * if another body parameter is already defined it will throw an exception at that point. However,
 * this shouldn't be an issue in most cases because query parameters are most commonly used with
 * GET methods anyway.
 */
public final class QueryMapAwareContract extends AbstractDelegatingContract {

    public QueryMapAwareContract(Contract delegate) {
        super(delegate);
    }

    @Override
    protected void processMetadata(Class<?> targetType, Method method, MethodMetadata metadata) {
        // if queryMapIndex is already set, the wrapped Contract already handled the annotation
        if (metadata.queryMapIndex() != null) {
            return;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            if (parameterAnnotations[i] != null) {
                for (Annotation annotation : parameterAnnotations[i]) {
                    Class<? extends Annotation> annotationType = annotation.annotationType();
                    if (annotationType == QueryMap.class) {
                        Preconditions.checkState(metadata.queryMapIndex() == null,
                                "QueryMap annotation was present on multiple parameters.");
                        metadata.queryMapIndex(i);

                        // if the query map parameter was interpreted as the body undo that
                        if (Integer.valueOf(i).equals(metadata.bodyIndex())) {
                            metadata.bodyIndex(null);
                            metadata.bodyType(null);
                        }
                    }
                }
            }
        }

        if (metadata.queryMapIndex() != null) {
            Preconditions.checkState(Map.class.isAssignableFrom(parameterTypes[metadata.queryMapIndex()]),
                    "QueryMap parameter must be a Map: %s", parameterTypes[metadata.queryMapIndex()]);
        }
    }

}
