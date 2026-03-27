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

import static com.palantir.conjure.java.client.jaxrs.Util.checkState;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines what annotations and values are valid on interfaces.
 */
@SuppressWarnings("MissingSummary")
interface Contract {

    /**
     * Called to parse the methods in the class that are linked to HTTP requests.
     *
     * @param targetType {@link Target#type() type} of the Feign interface.
     */
    List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType);

    abstract class BaseContract implements Contract {

        @Override
        public List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType) {
            checkState(
                    targetType.getTypeParameters().length == 0,
                    "Parameterized types unsupported: %s",
                    targetType.getSimpleName());
            checkState(
                    targetType.getInterfaces().length <= 1,
                    "Only single inheritance supported: %s",
                    targetType.getSimpleName());
            if (targetType.getInterfaces().length == 1) {
                checkState(
                        targetType.getInterfaces()[0].getInterfaces().length == 0,
                        "Only single-level inheritance supported: %s",
                        targetType.getSimpleName());
            }
            Map<String, MethodMetadata> result = new LinkedHashMap<String, MethodMetadata>();
            for (Method method : targetType.getMethods()) {
                if (method.getDeclaringClass() == Object.class
                        || (method.getModifiers() & Modifier.STATIC) != 0
                        || Util.isDefault(method)) {
                    continue;
                }
                MethodMetadata metadata = parseAndValidateMetadata(targetType, method);
                checkState(
                        !result.containsKey(metadata.configKey()), "Overrides unsupported: %s", metadata.configKey());
                result.put(metadata.configKey(), metadata);
            }
            return new ArrayList<MethodMetadata>(result.values());
        }

        /**
         * Called indirectly by {@link #parseAndValidateMetadata(Class)}.
         */
        MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
            MethodMetadata data = new MethodMetadata();
            data.returnType(Types.resolve(targetType, targetType, method.getGenericReturnType()));
            data.configKey(Feign.configKey(targetType, method));

            if (targetType.getInterfaces().length == 1) {
                processAnnotationOnClass(data, targetType.getInterfaces()[0]);
            }
            processAnnotationOnClass(data, targetType);

            for (Annotation methodAnnotation : method.getAnnotations()) {
                processAnnotationOnMethod(data, methodAnnotation, method);
            }
            checkState(
                    data.template().method() != null,
                    "Method %s not annotated with HTTP method type (ex. GET, POST)",
                    method.getName());
            Class<?>[] parameterTypes = method.getParameterTypes();

            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            int count = parameterAnnotations.length;
            for (int i = 0; i < count; i++) {
                boolean isHttpAnnotation = false;
                if (parameterAnnotations[i] != null) {
                    isHttpAnnotation = processAnnotationsOnParameter(data, parameterAnnotations[i], i);
                }
                if (parameterTypes[i] == URI.class) {
                    data.urlIndex(i);
                } else if (!isHttpAnnotation) {
                    checkState(data.formParams().isEmpty(), "Body parameters cannot be used with form parameters.");
                    checkState(data.bodyIndex() == null, "Method has too many Body parameters: %s", method);
                    data.bodyIndex(i);
                    data.bodyType(Types.resolve(targetType, targetType, method.getGenericParameterTypes()[i]));
                }
            }

            if (data.headerMapIndex() != null) {
                checkState(
                        Map.class.isAssignableFrom(parameterTypes[data.headerMapIndex()]),
                        "HeaderMap parameter must be a Map: %s",
                        parameterTypes[data.headerMapIndex()]);
            }

            if (data.queryMapIndex() != null) {
                checkState(
                        Map.class.isAssignableFrom(parameterTypes[data.queryMapIndex()]),
                        "QueryMap parameter must be a Map: %s",
                        parameterTypes[data.queryMapIndex()]);
            }

            return data;
        }

        /**
         * Called by parseAndValidateMetadata twice, first on the declaring class, then on the
         * target type (unless they are the same).
         *
         * @param data       metadata collected so far relating to the current java method.
         * @param clz        the class to process
         */
        abstract void processAnnotationOnClass(MethodMetadata data, Class<?> clz);

        /**
         * @param data       metadata collected so far relating to the current java method.
         * @param annotation annotations present on the current method annotation.
         * @param method     method currently being processed.
         */
        abstract void processAnnotationOnMethod(MethodMetadata data, Annotation annotation, Method method);

        /**
         * @param data        metadata collected so far relating to the current java method.
         * @param annotations annotations present on the current parameter annotation.
         * @param paramIndex  if you find a name in {@code annotations}, call {@link
         *                    #nameParam(MethodMetadata, String, int)} with this as the last parameter.
         * @return true if you called {@link #nameParam(MethodMetadata, String, int)} after finding an
         * http-relevant annotation.
         */
        abstract boolean processAnnotationsOnParameter(
                MethodMetadata data, Annotation[] annotations, int paramIndex);

        Collection<String> addTemplatedParam(Collection<String> possiblyNull, String name) {
            if (possiblyNull == null) {
                possiblyNull = new ArrayList<String>();
            }
            possiblyNull.add(String.format("{%s}", name));
            return possiblyNull;
        }

        /**
         * links a parameter name to its index in the method signature.
         */
        void nameParam(MethodMetadata data, String name, int i) {
            Collection<String> names =
                    data.indexToName().containsKey(i) ? data.indexToName().get(i) : new ArrayList<String>();
            names.add(name);
            data.indexToName().put(i, names);
        }
    }
}
