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

package feign.jaxrs;

import feign.Contract;
import feign.MethodMetadata;
import feign.QueryMap;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Contract that provides the functionality of {@link JAXRSContract} with full support for
 * the {@link QueryMap} annotation. Allows the QueryMap annotation to be used in methods
 * that also take a body parameter (such as {@link javax.ws.rs.POST or {@link javax.ws.rs.PUT}}).
 */
@SuppressWarnings("checkstyle:abbreviationaswordinname")
public final class JAXRSWithQueryMapContract extends Contract.BaseContract {

    private final JAXRSContract delegate = new JAXRSContract();

    @Override
    protected MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
        MethodMetadata data = super.parseAndValidateMetadata(targetType, method);

        // perform extra validation for QueryMap annotation
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (data.queryMapIndex() != null) {
            feign.Util.checkState(Map.class.isAssignableFrom(parameterTypes[data.queryMapIndex()]),
                    "QueryMap parameter must be a Map: %s", parameterTypes[data.queryMapIndex()]);
        }

        return data;
    }

    @Override
    protected void processAnnotationOnClass(MethodMetadata data, Class<?> clz) {
        delegate.processAnnotationOnClass(data, clz);
    }

    @Override
    protected void processAnnotationOnMethod(MethodMetadata data, Annotation annotation, Method method) {
        delegate.processAnnotationOnMethod(data, annotation, method);
    }

    @Override
    protected boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex) {
        boolean isHttpParam = delegate.processAnnotationsOnParameter(data, annotations, paramIndex);

        // if any of the annotations on the parameter is a QueryMap parameter, interpret it
        for (Annotation parameterAnnotation : annotations) {
            Class<? extends Annotation> annotationType = parameterAnnotation.annotationType();
            if (annotationType == QueryMap.class) {
                feign.Util.checkState(data.queryMapIndex() == null,
                        "QueryMap annotation was present on multiple parameters.");
                data.queryMapIndex(paramIndex);
                isHttpParam = true;
            }
        }

        return isHttpParam;
    }
}
